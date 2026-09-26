package com.dronzer.aisearch.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a Gemini request cannot be completed.
 *
 * <p>The upstream HTTP status is preserved as an integer so the failure
 * category stays observable, but no provider request body, response body,
 * header, or credential is retained. The message and code are fixed,
 * allow-listed strings derived only from the status.</p>
 */
public class GeminiUpstreamException extends RuntimeException {

    public static final String CODE = "GEMINI_UNAVAILABLE";
    public static final String QUOTA_CODE = "GEMINI_QUOTA_EXCEEDED";
    public static final String BAD_REQUEST_CODE = "GEMINI_BAD_REQUEST";
    public static final String FORBIDDEN_CODE = "GEMINI_FORBIDDEN";

    private static final String DEFAULT_MESSAGE = "Gemini service is temporarily unavailable";
    private static final String QUOTA_MESSAGE = "Gemini request quota is currently exhausted";
    private static final String BAD_REQUEST_MESSAGE = "Gemini rejected the request as invalid";
    private static final String FORBIDDEN_MESSAGE = "Gemini denied access to the request";

    private final Integer upstreamStatus;

    public GeminiUpstreamException() {
        this(null);
    }

    public GeminiUpstreamException(Integer upstreamStatus) {
        super(messageFor(upstreamStatus), null, false, false);
        this.upstreamStatus = upstreamStatus;
    }

    /** The upstream HTTP status, or {@code null} for a non-status transport failure. */
    public Integer upstreamStatus() {
        return upstreamStatus;
    }

    /** Stable, safe application error code for this failure. */
    public String code() {
        return switch (category(upstreamStatus)) {
            case QUOTA -> QUOTA_CODE;
            case BAD_REQUEST -> BAD_REQUEST_CODE;
            case FORBIDDEN -> FORBIDDEN_CODE;
            case UNAVAILABLE -> CODE;
        };
    }

    /** The application HTTP status surfaced to callers. */
    public HttpStatus applicationStatus() {
        return switch (category(upstreamStatus)) {
            case QUOTA -> HttpStatus.TOO_MANY_REQUESTS;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
    }

    private static String messageFor(Integer upstreamStatus) {
        return switch (category(upstreamStatus)) {
            case QUOTA -> QUOTA_MESSAGE;
            case BAD_REQUEST -> BAD_REQUEST_MESSAGE;
            case FORBIDDEN -> FORBIDDEN_MESSAGE;
            case UNAVAILABLE -> DEFAULT_MESSAGE;
        };
    }

    private enum Category {
        QUOTA, BAD_REQUEST, FORBIDDEN, UNAVAILABLE
    }

    private static Category category(Integer upstreamStatus) {
        if (upstreamStatus == null) {
            return Category.UNAVAILABLE;
        }
        return switch (upstreamStatus) {
            case 429 -> Category.QUOTA;
            case 400 -> Category.BAD_REQUEST;
            case 403 -> Category.FORBIDDEN;
            default -> Category.UNAVAILABLE;
        };
    }
}