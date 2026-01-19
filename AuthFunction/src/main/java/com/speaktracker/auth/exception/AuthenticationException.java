package com.speaktracker.auth.exception;

/**
 * 인증 실패 예외 (로그인 실패, 이메일 미인증 등)
 */
public class AuthenticationException extends AuthException {
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
