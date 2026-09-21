package com.dronzer.aisearch.dto;

/**
 * Unified retrieval result returned through the Phase 2A hybrid RAG pipeline.
 *
 * <p>Phase 1 carried only the cosine {@code similarity} from vector search. Phase 2A
 * retrieves candidates from two independent signals, so the record now carries all
 * three scores distinctly so callers never confuse them:
 *
 * <ul>
 *   <li>{@code similarity} - cosine similarity from vector search ({@code 1 - distance}).
 *       {@code null} when the chunk was <em>not</em> returned by the semantic path
 *       (a keyword-only hit). It is <b>never</b> the keyword relevance score.</li>
 *   <li>{@code keywordScore} - normalized {@code [0, 1]} PostgreSQL full-text
 *       relevance ({@code ts_rank_cd}) for keyword hits; {@code 0.0} for chunks found
 *       only by vector search.</li>
 *   <li>{@code hybridScore} - {@code semanticWeight * similarity + keywordWeight *
 *       keywordScore} (treating a {@code null} similarity as {@code 0.0}) used by the
 *       ranker for deterministic ordering.</li>
 * </ul>
 *
 * <p>The 5-argument constructor preserves the Phase 1 contract for callers that only
 * have a cosine similarity: it marks the chunk as a pure semantic hit
 * ({@code keywordScore = 0.0}) and seeds {@code hybridScore} with the cosine so that
 * ordering by hybridScore reproduces Phase 1 ordering (rank by similarity) when
 * keyword search is disabled.
 */
public record SemanticSearchResult(
        Long documentId,
        String filename,
        Integer chunkIndex,
        String chunkText,
        Double similarity,
        double keywordScore,
        double hybridScore) {

    /**
     * Phase 1 single-signal constructor: a pure cosine similarity hit.
     *
     * <p>Carries a real semantic score, no keyword signal ({@code keywordScore = 0.0}),
     * and seeds {@code hybridScore} with the cosine so that ordering by hybridScore
     * reproduces Phase 1 ordering (rank by similarity) when keyword search is off.
     */
    public SemanticSearchResult(
            Long documentId,
            String filename,
            Integer chunkIndex,
            String chunkText,
            double similarity) {
        this(documentId, filename, chunkIndex, chunkText, similarity, 0.0, similarity);
    }
}
