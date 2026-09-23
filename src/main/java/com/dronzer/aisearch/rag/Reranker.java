package com.dronzer.aisearch.rag;

import java.util.List;
import java.util.Optional;

/**
 * Contract for a future reranker that refines the ordering of evidence
 * candidates after initial retrieval and merging.
 *
 * <p>This is a contract only for Phase 2B-0. No implementation is provided.
 * The interface is intentionally general so that later phases can plug in a
 * cross-encoder model, a learned ranker, or any other reranking technology
 * without changing the rest of the pipeline.</p>
 *
 * <p>The input is a list of evidence candidates and the original query. The
 * output is a reordered list with optional reranking metadata. Implementations
 * may return an empty list if reranking is not possible, but they must not
 * throw for well-formed input unless a genuine failure occurs.</p>
 */
public interface Reranker {

    /**
     * Rerank the given evidence candidates for the given query.
     *
     * @param query         the original user query, never null
     * @param candidates   the evidence candidates to rerank, never null, may be empty
     * @param config        optional reranking configuration, may be null
     * @return the reranked candidates with metadata
     * @throws RerankingException if reranking cannot be performed
     */
    RerankResult rerank(String query, List<Evidence> candidates, RerankerConfig config);

    /**
     * A named configuration snapshot for a reranker.
     */
    record RerankerConfig(
            /** Optional name of the reranking model or strategy. */
            String name,
            /** Optional configuration parameters. */
            String configJson
    ) {
    }

    /**
     * Metadata about a reranking operation.
     */
    record RerankingMetadata(
            /** Optional name of the reranker used. */
            String rerankerName,
            /** Whether the reranker's output is deterministic for a given input. */
            boolean deterministic,
            /** Optional description of any tuning or model version. */
            String description
    ) {
    }
}
