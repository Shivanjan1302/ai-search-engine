package com.dronzer.aisearch.exception;

public class GeminiUpstreamException extends RuntimeException {

    public GeminiUpstreamException() {
        super("Gemini service is temporarily unavailable");
    }
}