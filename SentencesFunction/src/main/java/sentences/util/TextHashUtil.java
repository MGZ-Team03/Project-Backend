package sentences.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TTS 캐시 키 생성 유틸.
 * TTSFunction의 TextHashUtil과 동일한 규칙을 유지해야 캐시 재사용이 가능함.
 */
public class TextHashUtil {

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

    public static String generateS3Key(String voiceId, String textHash) {
        return String.format("audio/%s/%s.mp3", voiceId, textHash);
    }
}

