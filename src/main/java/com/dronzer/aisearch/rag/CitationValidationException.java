package com.dronzer.aisearch.rag;

/**
 * Thrown when citation/validation cannot be completed.
 *
 * <p>This is a contract only for Phase 2B-0.</p>
 */
public class CitationValidationException extends RuntimeException {

    public CitationValidationException(String message) {
        super(message);
    }

    public CitationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
