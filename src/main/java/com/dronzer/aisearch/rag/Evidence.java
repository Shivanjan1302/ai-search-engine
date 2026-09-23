package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;

/**
 * A single piece of evidence that may be presented to generation, citation
 * validation, or provenance assembly.
 *
 * <p>This is the foundation contract for Phase 2B-0. Every retrieval path,
 * including document, web, and any future provider, must be able to produce
 * a {@link Evidence}. Model knowledge is deliberately not represented as a
 * {@link Evidence} here, because model knowledge is not retrieved external
 * material; it is represented separately as {@link ModelKnowledgeMetadata}
 * only at generation time.</p>
 *
 * <p>Implementations must preserve the {@link KnowledgeSource} so that later
 * stages can distinguish document evidence from web evidence without relying
 * on heuristics.</p>
 */
public interface Evidence {

    /**
     * The knowledge source that produced this evidence.
     */
    KnowledgeSource source();

    /**
     * The text content of the evidence.
     * Must never be null.
     */
    String content();

    /**
     * A stable, provider-specific identifier for this evidence.
     * For document evidence this is typically the chunk id or a combination
     * of document id and chunk index. For web evidence this is typically the
     * URL. Must never be null.
     */
    String id();

    /**
     * Human-readable provenance metadata.
     * This may include filename, URL, publisher, author, publication date,
     * chunk index, or any other context that helps a future UI or audit log
     * explain where the evidence came from. May be null.
     */
    String provenance();

    /**
     * A numeric relevance or confidence score if one is available.
     * {@code null} means no score is available.
     * Scores from different providers are <strong>not</strong> assumed to be
     * comparable.
     */
    Double score();
}

