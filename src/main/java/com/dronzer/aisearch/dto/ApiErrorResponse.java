package com.dronzer.aisearch.dto;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String code) {

    public ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path) {

        this(timestamp, status, error, message, path, null);
    }
}