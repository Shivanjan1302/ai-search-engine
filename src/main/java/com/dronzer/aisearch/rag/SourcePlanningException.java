package com.dronzer.aisearch.rag;

/**
 * Thrown when source planning cannot be completed at all.
 *
 * <p>This is a contract only for Phase 2B-0. It is intended to be distinct
 * from downstream failures such as retrieval or generation failures.</p>
 */
public class SourcePlanningException extends RuntimeException {

    public SourcePlanningException(String message) {
        super(message);
    }

    public SourcePlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
