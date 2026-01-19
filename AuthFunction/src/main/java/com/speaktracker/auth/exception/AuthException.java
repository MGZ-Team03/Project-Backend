package com.speaktracker.auth.exception;

/**
 * 인증 관련 예외의 부모 클래스
 */
public class AuthException extends RuntimeException {
    
    public AuthException(String message) {
        super(message);
    }
    
    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
