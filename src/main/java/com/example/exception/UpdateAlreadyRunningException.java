package com.example.exception;

public class UpdateAlreadyRunningException extends RuntimeException {

    public UpdateAlreadyRunningException(String message) {
        super(message);
    }
}
