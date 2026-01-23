package com.speaktracker.tts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class PollyService {

    private final PollyClient pollyClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PollyService(PollyClient pollyClient) {
        this.pollyClient = pollyClient;
    }

    /**
     * 텍스트를 음성으로 변환 (Joanna 여성 음성, Neural 엔진)
     */
    public byte[] synthesizeSpeech(String text, String voiceId) {
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
            .text(text)
            .voiceId(VoiceId.fromValue(voiceId))
            .engine(Engine.NEURAL)
            .outputFormat(OutputFormat.MP3)
            .build();

        try (ResponseInputStream<SynthesizeSpeechResponse> responseStream =
                 pollyClient.synthesizeSpeech(request)) {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = responseStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Polly response stream", e);
        }
    }

    /**
     * SpeechMarks 기반 duration(ms) 추출.
     * - Polly의 time은 "mark 시작 시각(ms)"이며, 정확한 끝 시각이 아닐 수 있음.
     * - 실패 시 null 반환(오디오 생성 자체는 성공으로 처리 가능).
     */
    public Long tryGetDurationMs(String text, String voiceId) {
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
            .text(text)
            .voiceId(VoiceId.fromValue(voiceId))
            .engine(Engine.NEURAL)
            .outputFormat(OutputFormat.JSON)
            .speechMarkTypes(SpeechMarkType.WORD)
            .build();

        try (ResponseInputStream<SynthesizeSpeechResponse> responseStream =
                 pollyClient.synthesizeSpeech(request)) {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = responseStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }

            String body = buffer.toString(StandardCharsets.UTF_8);
            if (body == null || body.isBlank()) {
                return null;
            }

            long maxTime = -1;
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = objectMapper.readValue(line, Map.class);
                    Object t = m.get("time");
                    if (t instanceof Number) {
                        maxTime = Math.max(maxTime, ((Number) t).longValue());
                    } else if (t != null) {
                        maxTime = Math.max(maxTime, Long.parseLong(String.valueOf(t)));
                    }
                } catch (Exception ignore) {
                    // 일부 라인 파싱 실패는 무시하고 계속
                }
            }

            return maxTime >= 0 ? maxTime : null;

        } catch (Exception e) {
            return null;
        }
    }
}
