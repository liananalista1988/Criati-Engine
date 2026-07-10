package com.criati.criati.engine.exception;

public class LeituraPdfException extends RuntimeException {

    public LeituraPdfException(String message) {
        super(message);
    }

    public LeituraPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}