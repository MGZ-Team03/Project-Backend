package com.speaktracker.auth.exception;

/**
 * JWT 토큰 검증 실패 예외
 */
public class InvalidTokenException extends AuthException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
