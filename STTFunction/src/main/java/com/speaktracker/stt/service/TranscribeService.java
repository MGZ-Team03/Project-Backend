package com.speaktracker.stt.service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.util.Base64;
import java.util.UUID;

public class TranscribeService {

    private final TranscribeClient transcribeClient;
    private final S3Client s3Client;
    private final String bucketName;

    public TranscribeService(TranscribeClient transcribeClient, S3Client s3Client, String bucketName) {
        this.transcribeClient = transcribeClient;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * 오디오 파일을 Transcribe로 변환
     *
     * @param audioData Base64 인코딩된 오디오 데이터 또는 원본 바이트
     * @param languageCode 언어 코드 (예: "en-US", "ko-KR")
     * @return 변환된 텍스트
     */
    public String transcribeAudio(byte[] audioData, String languageCode) throws Exception {
        // 1. S3에 오디오 파일 업로드
        String jobName = "transcribe-job-" + UUID.randomUUID().toString();
        String s3Key = "transcribe-temp/" + jobName + ".webm";

        uploadToS3(audioData, s3Key);

        // 2. Transcribe Job 시작
        String s3Uri = String.format("s3://%s/%s", bucketName, s3Key);
        startTranscriptionJob(jobName, s3Uri, languageCode);

        // 3. Job 완료 대기 (최대 60초)
        String transcript = waitForJobCompletion(jobName);

        // 4. S3에서 임시 파일 삭제
        deleteFromS3(s3Key);

        return transcript;
    }

    private void uploadToS3(byte[] audioData, String s3Key) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("audio/webm")
                .build();

        s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(audioData));
    }

    private void startTranscriptionJob(String jobName, String s3Uri, String languageCode) {
        Media media = Media.builder()
                .mediaFileUri(s3Uri)
                .build();

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .media(media)
                .mediaFormat(MediaFormat.WEBM)
                .languageCode(languageCode)
                .build();

        transcribeClient.startTranscriptionJob(request);
    }

    private String waitForJobCompletion(String jobName) throws Exception {
        int maxAttempts = 30; // 30초 (1초 간격)
        int attempts = 0;

        while (attempts < maxAttempts) {
            GetTranscriptionJobRequest getRequest = GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .build();

            GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(getRequest);
            TranscriptionJob job = response.transcriptionJob();

            TranscriptionJobStatus status = job.transcriptionJobStatus();

            if (status == TranscriptionJobStatus.COMPLETED) {
                // Transcript URI에서 결과 가져오기
                String transcriptUri = job.transcript().transcriptFileUri();
                return fetchTranscriptFromUri(transcriptUri);
            } else if (status == TranscriptionJobStatus.FAILED) {
                throw new RuntimeException("Transcription job failed: " + job.failureReason());
            }

            // 1초 대기
            Thread.sleep(1000);
            attempts++;
        }

        throw new RuntimeException("Transcription job timeout");
    }

    private String fetchTranscriptFromUri(String uri) throws Exception {
        // Transcribe 결과 URI에서 JSON 다운로드 및 파싱
        // 간단하게 URL에서 직접 fetch
        java.net.URL url = new java.net.URL(uri);
        java.io.InputStream is = url.openStream();
        String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        is.close();

        // JSON 파싱 (간단한 정규식 사용)
        // 실제로는 Jackson ObjectMapper 사용 권장
        int transcriptStart = json.indexOf("\"transcript\":\"") + 14;
        int transcriptEnd = json.indexOf("\"", transcriptStart);

        if (transcriptStart > 13 && transcriptEnd > transcriptStart) {
            return json.substring(transcriptStart, transcriptEnd);
        }

        return "";
    }

    private void deleteFromS3(String s3Key) {
        try {
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest deleteRequest =
                software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            // 삭제 실패해도 무시 (임시 파일)
        }
    }
}
