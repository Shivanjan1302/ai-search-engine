package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.SemanticSearchResult;
import java.util.List;

/**
 * Pure translation from {@link SemanticSearchResult} to {@link DocumentEvidence}.
 *
 * <p>This adapter exists so that retrieval results can flow into the new
 * evidence model without losing provenance metadata (filename, chunk index,
 * similarity, keyword/hybrid scores). It performs no new retrieval logic and
 * no duplicate tenant filtering.</p>
 *
 * <p>The adapter is stateless and thread-safe.</p>
 */
public final class DocumentRetrievalAdapter {

    private DocumentRetrievalAdapter() {
        // utility class
    }

    /**
     * Convert a list of {@link SemanticSearchResult} into {@link DocumentEvidence}.
     *
     * @param results the semantic search results to convert, never null
     * @return the corresponding document evidence, in the same order
     */
    public static List<DocumentEvidence> fromResults(List<SemanticSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .map(DocumentRetrievalAdapter::fromResult)
                .toList();
    }

    /**
     * Convert a single {@link SemanticSearchResult} into {@link DocumentEvidence}.
     *
     * @param result the semantic search result to convert, never null
     * @return the corresponding document evidence
     */
    public static DocumentEvidence fromResult(SemanticSearchResult result) {
        if (result == null) {
            throw new IllegalArgumentException("SemanticSearchResult must not be null");
        }
        return new DocumentEvidence(
                result.documentId(),
                result.filename(),
                result.chunkIndex(),
                result.chunkText(),
                result.similarity(),
                result.keywordScore(),
                result.hybridScore(),
                computeRetrievalScore(result),
                "semantic"
        );
    }

    /**
     * Compute the retrieval score to store.
     * Uses hybridScore for true hybrid results (both signals present),
     * otherwise falls back to the primary signal (similarity or keywordScore).
     */
    private static double computeRetrievalScore(SemanticSearchResult result) {
        if (result.similarity() != null && result.keywordScore() > 0.0) {
            // True hybrid: both signals present
            return result.hybridScore();
        }
        if (result.similarity() != null && result.similarity() > 0.0) {
            // Semantic only
            return result.similarity();
        }
        // Keyword only (similarity is null)
        return result.keywordScore();
    }
}
