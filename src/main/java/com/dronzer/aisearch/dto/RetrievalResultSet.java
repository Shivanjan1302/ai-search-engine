package com.dronzer.aisearch.dto;

import java.util.List;

/**
 * Aggregation of retrieval results across one or more knowledge sources.
 *
 * <p>This wrapper is a deliberate structural choice for Phase 2B-0. It keeps
 * the existing {@link SemanticSearchResult} and {@link WebSearchResult} types
 * intact while giving downstream pipeline stages a single collection they can
 * reason about without coupling retrieval to response DTOs.</p>
 */
public record RetrievalResultSet(
        List<SemanticSearchResult> semanticResults,
        List<WebSearchResult> webResults
) {

    public RetrievalResultSet {
        semanticResults = List.copyOf(semanticResults == null ? List.of() : semanticResults);
        webResults = List.copyOf(webResults == null ? List.of() : webResults);
    }

    /**
     * Creates a {@link RetrievalResultSet} from document retrieval results only.
     */
    public static RetrievalResultSet fromSemantic(List<SemanticSearchResult> semanticResults) {
        return new RetrievalResultSet(semanticResults, List.of());
    }

    /**
     * Creates a {@link RetrievalResultSet} from web retrieval results only.
     */
    public static RetrievalResultSet fromWeb(List<WebSearchResult> webResults) {
        return new RetrievalResultSet(List.of(), webResults);
    }

    /** Total candidate count across all sources. */
    public int totalCount() {
        return semanticResults.size() + webResults.size();
    }
}
