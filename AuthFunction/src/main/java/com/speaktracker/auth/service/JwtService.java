package com.speaktracker.auth.service;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.speaktracker.auth.exception.InvalidTokenException;

/**
 * JWT 토큰 파싱 및 검증 서비스
 */
public class JwtService {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Authorization 헤더에서 email 직접 추출
     * @param authHeader Authorization 헤더 값
     * @return email
     * @throws InvalidTokenException 토큰이 유효하지 않은 경우
     */
    public static String extractEmailFromAuthHeader(String authHeader) {
        String token = extractBearerToken(authHeader);
        return extractEmail(token);
    }
    
    /**
     * JWT 토큰에서 email 추출
     * @param token JWT 토큰
     * @return email 또는 null (추출 불가능한 경우)
     * @throws InvalidTokenException 토큰이 유효하지 않은 경우
     */
    public static String extractEmail(String token) {
        if (token == null || token.isEmpty()) {
            throw new InvalidTokenException("토큰이 null이거나 비어있습니다.");
        }
        
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new InvalidTokenException("유효하지 않은 토큰 형식입니다.");
        }
        
        try {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            String email = (String) claims.get("email");
            
            if (email == null || email.isEmpty()) {
                throw new InvalidTokenException("토큰에서 이메일을 찾을 수 없습니다.");
            }
            
            return email;
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("JWT 토큰 파싱 실패: " + e.getMessage());
        }
    }
    
    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     * @param authHeader Authorization 헤더 값
     * @return Bearer 토큰
     * @throws InvalidTokenException 토큰이 유효하지 않은 경우
     */
    public static String extractBearerToken(String authHeader) {
        if (authHeader == null) {
            throw new InvalidTokenException("인증 토큰이 필요합니다.");
        }
        
        if (!authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization 헤더 형식이 올바르지 않습니다.");
        }
        
        String token = authHeader.substring(7);
        if (token.isEmpty()) {
            throw new InvalidTokenException("Bearer 토큰이 비어있습니다.");
        }
        
        return token;
    }
}
