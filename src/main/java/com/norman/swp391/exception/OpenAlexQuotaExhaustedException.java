package com.norman.swp391.exception;

public class OpenAlexQuotaExhaustedException extends RuntimeException {
    public OpenAlexQuotaExhaustedException(String message) {
        super(message);
    }
    public OpenAlexQuotaExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
