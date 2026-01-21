package com.speaktracker.tutorRegister.exceptions;

/**
 * 튜터 등록 기능의 모든 예외를 포함하는 클래스
 */
public class TutorRegisterExceptions {

    /**
     * Base Exception
     */
    public static class TutorRegisterException extends RuntimeException {
        private final String errorCode;

        public TutorRegisterException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    // ===== 404 Exceptions =====
    
    public static class TutorNotFoundException extends TutorRegisterException {
        public TutorNotFoundException(String message) {
            super("TUTOR_NOT_FOUND", message);
        }
    }

    public static class RequestNotFoundException extends TutorRegisterException {
        public RequestNotFoundException(String message) {
            super("REQUEST_NOT_FOUND", message);
        }
    }

    // ===== 403 Exceptions =====
    
    public static class UnauthorizedException extends TutorRegisterException {
        public UnauthorizedException(String message) {
            super("UNAUTHORIZED", message);
        }
    }

    // ===== 400 Exceptions =====
    
    public static class DuplicateRequestException extends TutorRegisterException {
        public DuplicateRequestException(String message) {
            super("DUPLICATE_REQUEST", message);
        }
    }

    public static class CapacityExceededException extends TutorRegisterException {
        public CapacityExceededException(String message) {
            super("CAPACITY_FULL", message);
        }
    }

    public static class TutorNotAcceptingException extends TutorRegisterException {
        public TutorNotAcceptingException(String message) {
            super("TUTOR_NOT_ACCEPTING", message);
        }
    }

    public static class AlreadyRegisteredException extends TutorRegisterException {
        public AlreadyRegisteredException(String message) {
            super("ALREADY_REGISTERED", message);
        }
    }

    public static class RequestAlreadyProcessedException extends TutorRegisterException {
        public RequestAlreadyProcessedException(String message) {
            super("REQUEST_ALREADY_PROCESSED", message);
        }
    }

    public static class CannotCancelException extends TutorRegisterException {
        public CannotCancelException(String message) {
            super("CANNOT_CANCEL", message);
        }
    }
}
