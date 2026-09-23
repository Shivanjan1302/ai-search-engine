package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.Objects;

/**
 * Evidence retrieved from the authenticated user's authorized documents.
 *
 * <p>This is a contract only for Phase 2B-0. It wraps the existing
 * {@link com.dronzer.aisearch.dto.SemanticSearchResult} shape so that
 * document evidence can flow through the new provenance model without
 * losing the chunk identity, similarity, keyword score, or hybrid score
 * that the current pipeline already produces.</p>
 *
 * <p>Tenant isolation is guaranteed upstream by {@link com.dronzer.aisearch.repository.VectorSearchRepository}
 * and {@link com.dronzer.aisearch.repository.KeywordSearchRepository}, which scope every
 * query to {@code d.user_id = ?}. This record does not re-check ownership;
 * it trusts that the retrieval layer has already enforced it.</p>
 */
public record DocumentEvidence(

        Long documentId,

        String filename,

        Integer chunkIndex,

        /**
         * The text content of this chunk.
         */
        String content,

        /**
         * Cosine similarity from vector search, or {@code null} if this chunk
         * was returned by keyword search only.
         */
        Double similarity,

        /**
         * Normalized keyword relevance, or {@code 0.0} if this chunk has no
         * keyword signal.
         */
        double keywordScore,

        /**
         * Weighted hybrid score used for ranking, or {@code 0.0} if no
         * ranking has been applied yet.
         */
        double hybridScore,

        /**
         * Provider-specific score from the retrieval layer, if any.
         * For semantic retrieval this is the cosine similarity; for keyword
         * retrieval this is the normalized keyword relevance.
         */
        Double retrievalScore,

        /**
         * Retrieval metadata such as the retrieval method that produced this
         * evidence (semantic, keyword, hybrid). May be null.
         */
        String retrievalMethod

) implements Evidence {

    @Override
    public KnowledgeSource source() {
        return KnowledgeSource.DOCUMENT;
    }

    @Override
    public String id() {
        return documentId + "::" + chunkIndex;
    }

    @Override
    public String provenance() {
        return filename + ", chunk " + chunkIndex;
    }

    @Override
    public Double score() {
        return hybridScore > 0.0 ? hybridScore : retrievalScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentEvidence that)) return false;
        return Objects.equals(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id());
    }
}
