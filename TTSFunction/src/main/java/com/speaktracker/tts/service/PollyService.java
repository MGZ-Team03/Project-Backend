package com.speaktracker.tts.service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PollyService {

    private final PollyClient pollyClient;

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
}
