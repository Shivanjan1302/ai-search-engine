package com.dronzer.aisearch.dto;

/**
 * The knowledge source that produced a piece of evidence or contributed to an answer.
 *
 * <p>This enum exists so that source identity is expressed as a type, not as a scattered
 * string literal, across retrieval, reranking, context construction, generation, citation
 * validation, and response provenance.</p>
 *
 * <p>{@code MODEL_KNOWLEDGE} is deliberately distinct from {@code DOCUMENT} and {@code WEB}.
 * It represents the model's own parametric knowledge used at generation time; it is <strong>not</strong>
 * retrieved external evidence and must never be treated as equivalent to a document chunk or a
 * web search result.</p>
 */
public enum KnowledgeSource {

    /**
     * Evidence retrieved from the authenticated user's authorized documents.
     * Tenant isolation applies absolutely to this source.
     */
    DOCUMENT,

    /**
     * Evidence retrieved from an external/web search provider (currently Tavily).
     */
    WEB,

    /**
     * Information drawn from the model's own parametric knowledge at generation time.
     * Not retrieved text; not externally verifiable in the same way as DOCUMENT or WEB.
     */
    MODEL_KNOWLEDGE
}
