package com.dronzer.aisearch.exception;

public class UploadSizeExceededException extends RuntimeException {

    public UploadSizeExceededException() {
        super("Uploaded file exceeds the maximum allowed size");
    }
}