package com.example.exception;

public class ExternalApiException extends RuntimeException {

    private final Integer statusCode;
    private final boolean retryable;

    public ExternalApiException(String message) {
        this(message, null, null, false);
    }

    public ExternalApiException(String message, Throwable cause) {
        this(message, cause, null, false);
    }

    public ExternalApiException(String message, Throwable cause, boolean retryable) {
        this(message, cause, null, retryable);
    }

    public ExternalApiException(String message, int statusCode, boolean retryable) {
        this(message, null, statusCode, retryable);
    }

    public ExternalApiException(String message, Throwable cause, Integer statusCode, boolean retryable) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
