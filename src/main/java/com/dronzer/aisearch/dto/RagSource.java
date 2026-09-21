package com.dronzer.aisearch.dto;

/**
 * A single source chunk returned to the caller as provenance for an answer.
 *
 * <p>The three relevance scores are exposed independently so the source metadata
 * never conflates them:
 * <ul>
 *   <li>{@code similarity} - cosine similarity from vector search ({@code 1 - distance}).
 *       {@code 0.0} for keyword-only chunks, which carry no semantic score; it is never
 *       the keyword relevance.</li>
 *   <li>{@code keywordScore} - normalized full-text relevance for keyword matches
 *       ({@code 0.0} for pure vector hits).</li>
 *   <li>{@code hybridScore} - the weighted combination used for ranking.</li>
 * </ul>
 */
public record RagSource(
        Long documentId,
        String filename,
        Integer chunkIndex,
        double similarity,
        double keywordScore,
        double hybridScore) {

    /** Backward-compatible single-signal source: cosine similarity only. */
    public RagSource(
            Long documentId,
            String filename,
            Integer chunkIndex,
            double similarity) {
        this(documentId, filename, chunkIndex, similarity, 0.0, similarity);
    }
}
