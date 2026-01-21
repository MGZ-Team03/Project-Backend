package com.speaktracker.tts.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TextHashUtil {

    /**
     * SHA-256 해시 생성 (텍스트 + 음성ID 조합)
     */
    public static String generateHash(String text, String voiceId) {
        try {
            String combined = text.trim().toLowerCase() + "|" + voiceId;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * S3 키 생성
     */
    public static String generateS3Key(String voiceId, String textHash) {
        return String.format("audio/%s/%s.mp3", voiceId, textHash);
    }
}
