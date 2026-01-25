package com.speaktracker.stt.service;

import com.speaktracker.stt.model.STTCredentialsResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.time.Instant;
import java.util.UUID;

public class STSCredentialsService {
    private final StsClient stsClient;
    private final String transcribeClientRoleArn;
    private final int credentialExpirationSeconds;

    public STSCredentialsService(StsClient stsClient, String transcribeClientRoleArn, int credentialExpirationSeconds) {
        this.stsClient = stsClient;
        this.transcribeClientRoleArn = transcribeClientRoleArn;
        this.credentialExpirationSeconds = credentialExpirationSeconds;
    }

    public STTCredentialsResponse getTemporaryCredentials(String languageCode, int sampleRate) {
        try {
            AssumeRoleRequest request = AssumeRoleRequest.builder()
                    .roleArn(transcribeClientRoleArn)
                    .roleSessionName("transcribe-client-" + UUID.randomUUID())
                    .durationSeconds(credentialExpirationSeconds)
                    .build();

            AssumeRoleResponse response = stsClient.assumeRole(request);
            Credentials creds = response.credentials();

            long expirationMillis = creds.expiration().toEpochMilli();

            return STTCredentialsResponse.success(
                    creds.accessKeyId(),
                    creds.secretAccessKey(),
                    creds.sessionToken(),
                    expirationMillis,
                    "ap-northeast-2",
                    languageCode,
                    sampleRate
            );

        } catch (Exception e) {
            return STTCredentialsResponse.error("Failed to generate credentials: " + e.getMessage());
        }
    }
}
