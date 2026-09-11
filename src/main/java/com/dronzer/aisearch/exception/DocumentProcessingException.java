package com.dronzer.aisearch.exception;

public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException() {
        super("Document could not be processed");
    }
}