package com.dronzer.aisearch.exception;

public class UnsupportedDocumentException extends RuntimeException {

    public UnsupportedDocumentException() {
        super("Only PDF and text documents are supported");
    }
}