package com.norman.swp391.exception;

public class AiQuotaExhaustedException extends RuntimeException {
    public AiQuotaExhaustedException(String message) {
        super(message);
    }
    public AiQuotaExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
