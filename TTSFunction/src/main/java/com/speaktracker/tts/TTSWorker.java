package com.speaktracker.tts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.tts.model.TTSJobMessage;
import com.speaktracker.tts.service.PollyService;
import com.speaktracker.tts.service.S3Service;
import com.speaktracker.tts.service.JobStatusService;
import com.speaktracker.tts.service.SentenceAudioService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class TTSWorker implements RequestHandler<SQSEvent, Void> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PollyService pollyService;
    private final S3Service s3Service;
    private final JobStatusService jobStatusService;
    private final SentenceAudioService sentenceAudioService;
    private final int presignedUrlExpiration;

    public TTSWorker() {
        // AWS 클라이언트 초기화
        PollyClient pollyClient = PollyClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        S3Client s3Client = S3Client.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        S3Presigner s3Presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        // 환경 변수
        String ttsBucket = System.getenv("TTS_BUCKET");
        String jobStatusTable = System.getenv("JOB_STATUS_TABLE");
        String sentenceAudioTable = System.getenv("SENTENCE_AUDIO_TABLE");
        this.presignedUrlExpiration = Integer.parseInt(
            System.getenv().getOrDefault("PRESIGNED_URL_EXPIRATION", "3600")
        );

        // 서비스 초기화
        this.pollyService = new PollyService(pollyClient);
        this.s3Service = new S3Service(s3Client, s3Presigner, ttsBucket);
        this.jobStatusService = new JobStatusService(dynamoDbClient, jobStatusTable);
        this.sentenceAudioService = (sentenceAudioTable == null || sentenceAudioTable.isBlank())
            ? null
            : new SentenceAudioService(dynamoDbClient, sentenceAudioTable);
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                context.getLogger().log("Processing TTS job from SQS: " + message.getMessageId());

                // SQS 메시지 파싱
                TTSJobMessage jobMessage = objectMapper.readValue(
                    message.getBody(), TTSJobMessage.class);

                String jobId = jobMessage.getJobId();
                String text = jobMessage.getText();
                String voiceId = jobMessage.getVoiceId();
                String s3Key = jobMessage.getS3Key();
                String sessionId = jobMessage.getSessionId();
                Integer sentenceIndex = jobMessage.getSentenceIndex();
                boolean trackDuration = jobMessage.isTrackDuration();

                context.getLogger().log(String.format(
                    "Job ID: %s, Text length: %d, Voice: %s",
                    jobId, text.length(), voiceId));

                // 캐시 확인 (이미 존재하면 Polly MP3 생략)
                boolean cached = s3Service.exists(s3Key);
                if (!cached) {
                    // Polly TTS 변환
                    context.getLogger().log("Calling Polly TTS...");
                    byte[] audioBytes = pollyService.synthesizeSpeech(text, voiceId);

                    // S3 업로드
                    context.getLogger().log("Uploading to S3: " + s3Key);
                    s3Service.uploadAudio(s3Key, audioBytes);
                } else {
                    context.getLogger().log("S3 cache hit - skip MP3 generation: " + s3Key);
                }

                // duration 추출(선택)
                Long durationMs = null;
                if (trackDuration) {
                    durationMs = pollyService.tryGetDurationMs(text, voiceId);
                }

                // Presigned URL 생성
                String presignedUrl = s3Service.generatePresignedUrl(s3Key, presignedUrlExpiration);

                // DynamoDB 상태 업데이트 (COMPLETED)
                jobStatusService.updateJobCompleted(jobId, presignedUrl);

                // 문장 연습 세션 레코드 업데이트 (Optional)
                if (sentenceAudioService != null && sessionId != null && !sessionId.isBlank()
                    && sentenceIndex != null) {
                    try {
                        sentenceAudioService.updateCompleted(
                            sessionId, sentenceIndex, s3Key, durationMs, voiceId, jobId
                        );
                    } catch (Exception e) {
                        context.getLogger().log("Failed to update SentenceAudioTable: " + e.getMessage());
                    }
                }

                context.getLogger().log("TTS job completed successfully: " + jobId);

            } catch (Exception e) {
                context.getLogger().log("Error processing TTS job: " + e.getMessage());
                e.printStackTrace();

                // 실패 상태 업데이트
                try {
                    TTSJobMessage jobMessage = objectMapper.readValue(
                        message.getBody(), TTSJobMessage.class);
                    jobStatusService.updateJobFailed(jobMessage.getJobId(), e.getMessage());

                    if (sentenceAudioService != null
                        && jobMessage.getSessionId() != null
                        && jobMessage.getSentenceIndex() != null) {
                        try {
                            sentenceAudioService.updateFailed(
                                jobMessage.getSessionId(),
                                jobMessage.getSentenceIndex(),
                                e.getMessage(),
                                "TTS_WORKER_ERROR"
                            );
                        } catch (Exception ignore) {
                            // noop
                        }
                    }
                } catch (Exception updateError) {
                    context.getLogger().log("Failed to update job status: " + updateError.getMessage());
                }

                // SQS DLQ로 전달되도록 예외 재발생
                throw new RuntimeException("TTS job processing failed", e);
            }
        }

        return null;
    }
}
