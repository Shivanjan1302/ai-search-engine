package com.dronzer.aisearch.rag;

/**
 * Thrown when a REQUIRED knowledge source cannot be consulted during retrieval
 * orchestration and the plan's {@link FallbackPolicy} is {@link FallbackPolicy#FAIL_FAST}.
 *
 * <p>This is the retrieval-stage member of the per-stage exception hierarchy in this
 * package. {@link SourcePlanningException} already states that planning failures are
 * "intended to be distinct from downstream failures such as retrieval or generation
 * failures", so a retrieval-stage type is the missing member of that hierarchy rather
 * than a new abstraction. It mirrors the exact shape of the other exceptions in this
 * package: unchecked, message plus optional cause.</p>
 *
 * <p>The original upstream cause (for example
 * {@code com.dronzer.aisearch.exception.WebSearchUpstreamException} or a document
 * lookup failure) is always preserved as the cause so no upstream context is lost.</p>
 *
 * <p>It is never thrown for OPTIONAL sources: optional-source failures are recorded in
 * {@link RetrievalResult#unavailableSources()} instead, so successful evidence from
 * other sources survives.</p>
 */
public class RetrievalException extends RuntimeException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
