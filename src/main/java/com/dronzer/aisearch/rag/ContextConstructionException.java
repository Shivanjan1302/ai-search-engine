package com.dronzer.aisearch.rag;

/**
 * Thrown when context construction fails.
 *
 * <p>This is a contract only for Phase 2B-0.</p>
 */
public class ContextConstructionException extends RuntimeException {

    public ContextConstructionException(String message) {
        super(message);
    }

    public ContextConstructionException(String message, Throwable cause) {
        super(message, cause);
    }
}
