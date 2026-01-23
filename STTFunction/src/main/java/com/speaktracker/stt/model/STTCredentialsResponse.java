package com.speaktracker.stt.model;

import java.util.HashMap;
import java.util.Map;

public class STTCredentialsResponse {
    private boolean success;
    private Map<String, String> credentials;
    private Map<String, Object> config;
    private String error;

    public STTCredentialsResponse() {
    }

    public static STTCredentialsResponse success(
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            long expiration,
            String region,
            String languageCode,
            int mediaSampleRateHertz) {

        STTCredentialsResponse response = new STTCredentialsResponse();
        response.success = true;

        response.credentials = new HashMap<>();
        response.credentials.put("accessKeyId", accessKeyId);
        response.credentials.put("secretAccessKey", secretAccessKey);
        response.credentials.put("sessionToken", sessionToken);
        response.credentials.put("expiration", String.valueOf(expiration));

        response.config = new HashMap<>();
        response.config.put("region", region);
        response.config.put("languageCode", languageCode);
        response.config.put("mediaSampleRateHertz", mediaSampleRateHertz);
        response.config.put("mediaEncoding", "pcm");

        return response;
    }

    public static STTCredentialsResponse error(String errorMessage) {
        STTCredentialsResponse response = new STTCredentialsResponse();
        response.success = false;
        response.error = errorMessage;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
