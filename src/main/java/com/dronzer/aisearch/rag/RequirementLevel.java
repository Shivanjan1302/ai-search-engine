package com.dronzer.aisearch.rag;

/**
 * How strongly a {@link com.dronzer.aisearch.rag.SourceRequirement}
 * requires its source.
 */
public enum RequirementLevel {

    /**
     * The source is required. If the source cannot be consulted, the pipeline
     * should report the failure rather than silently degrading.
     */
    REQUIRED,

    /**
     * The source is optional. The pipeline may proceed without it if it is
     * unavailable or returns no evidence.
     */
    OPTIONAL
}
