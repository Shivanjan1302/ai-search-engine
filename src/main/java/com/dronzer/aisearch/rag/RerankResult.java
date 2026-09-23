package com.dronzer.aisearch.rag;

import java.util.List;

/**
 * The output of a reranking operation.
 *
 * <p>This is a shared contract type used by {@link Reranker} and
 * {@link ContextBuilder}. It is defined as a top-level record so that
 * {@link ContextBuilder} can reference it without depending on the
 * enclosing declaration of {@link Reranker}.</p>
 */
public record RerankResult(
        /** The reranked evidence candidates, in descending relevance order. */
        List<Evidence> ranked,
                /** Optional metadata about the reranking operation. */
        Reranker.RerankingMetadata metadata
) {
}
