package com.speaktracker.auth.exception;

/**
 * 사용자를 찾을 수 없음 예외
 */
public class UserNotFoundException extends AuthException {
    
    public UserNotFoundException(String message) {
        super(message);
    }
    
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
