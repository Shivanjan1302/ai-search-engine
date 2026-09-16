package com.dronzer.aisearch.exception;

public class GeminiUpstreamException extends RuntimeException {

    public static final String CODE = "GEMINI_UNAVAILABLE";

    public GeminiUpstreamException() {
        super("Gemini service is temporarily unavailable");
    }
}