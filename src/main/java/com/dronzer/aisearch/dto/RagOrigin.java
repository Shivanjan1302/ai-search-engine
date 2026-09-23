package com.dronzer.aisearch.dto;

/**
 * Origin of the information that produced a RAG response.
 *
 * <p>For Phase 2B-0 this enum is intentionally kept small and semantically
 * precise. It captures the four distinct cases the system must be able to
 * distinguish in responses.</p>
 *
 * <p>{@link #MODEL_KNOWLEDGE} covers both standalone model knowledge and model
 * knowledge used to supplement retrieved evidence. If a response mixes model
 * knowledge with retrieved evidence in a way that must be distinguished from
 * pure retrieved evidence, the response should use {@link #MIXED}.</p>
 */
public enum RagOrigin {

    /** The response is grounded in the authenticated user's authorized documents. */
    DOCUMENTS,

    /** The response is grounded in external/web search results. */
    WEB,

    /** The response is grounded in the model's own parametric knowledge. */
    MODEL_KNOWLEDGE,

    /**
     * The response uses more than one source type and the source mix matters.
     * For example: a response that combines document evidence with web evidence.
     * This replaces the legacy {@code DOCUMENTS_AND_WEB} concept with a more
     * general mixed-source state.
     */
    MIXED,

    /** There is not enough evidence to produce a grounded response. */
    INSUFFICIENT_EVIDENCE
}
