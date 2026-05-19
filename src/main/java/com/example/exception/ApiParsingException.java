package com.example.exception;

public class ApiParsingException extends RuntimeException {

    public ApiParsingException(String message) {
        super(message);
    }

    public ApiParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
