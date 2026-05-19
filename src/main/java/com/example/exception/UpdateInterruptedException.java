package com.example.exception;

public class UpdateInterruptedException extends RuntimeException {

    public UpdateInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
