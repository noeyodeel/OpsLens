package com.opslens.application.slack;

public class InvalidSlackCommandException extends RuntimeException {

    public InvalidSlackCommandException(String message) {
        super(message);
    }
}
