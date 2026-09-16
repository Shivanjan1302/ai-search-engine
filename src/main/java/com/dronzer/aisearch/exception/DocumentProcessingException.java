package com.dronzer.aisearch.exception;

public class DocumentProcessingException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Document could not be processed";

    public DocumentProcessingException() {
        super(DEFAULT_MESSAGE);
    }

    public DocumentProcessingException(String message) {
        super(message);
    }
}