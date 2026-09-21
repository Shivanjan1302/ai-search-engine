package com.dronzer.aisearch.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.dronzer.aisearch.dto.KeywordSearchResult;
import com.dronzer.aisearch.dto.SemanticSearchResult;

/**
 * Combines semantic (vector) and keyword (full-text) retrieval results.
 *
 * <p>This is the {@code Candidate merger} component of Phase 2A. It performs three
 * responsibilities:
 *
 * <ol>
 *   <li><strong>Deduplication</strong> - a chunk that is returned by both searches is
 *       identified by the stable key {@code (documentId, chunkIndex)} and kept only
 *       once. The semantic cosine score is preserved as the authoritative
 *       {@code similarity} value; the keyword score is attached so the ranker can
 *       combine both signals.</li>
 *   <li><strong>Score normalization</strong> - raw {@code ts_rank_cd} keyword scores
 *       are scaled to {@code [0, 1]} relative to the strongest keyword hit in the same
 *       batch, so the two score families can be weighted together.</li>
  *   <li><strong>Gap filling</strong> - chunks present only in the keyword result set
 *       are included (these are the cases where vector search would miss an exact
 *       name, identifier or error code). They have <em>no</em> semantic score, so
 *       {@code similarity} is set to {@code null} - it is never the keyword relevance
 *       (that lives in {@code keywordScore}). The ranker treats a {@code null}
 *       similarity as zero for the weighted hybrid score.</li>
 * </ol>
 *
 * <p>Tenant isolation is guaranteed upstream: the semantic candidates are already
 * scoped to the user by {@code VectorSearchRepository}, and the keyword candidates by
 * {@code KeywordSearchRepository}. This component only merges what it is given and
 * never reintroduces chunks belonging to another tenant.
 */
@Component
public class HybridCandidateMerger {

    /** Immutable identity used to deduplicate a chunk across the two result sets. */
    record ChunkKey(Long documentId, Integer chunkIndex) {
    }

    /**
     * Merges semantic and keyword candidates into a single list.
     *
     * <p>The returned list is ordered semantically-first (in their original ranking
     * order) followed by keyword-only chunks. Ordering is finalized by the
     * {@link HybridRanker}; this method only guarantees a stable, deduplicated set
     * with the per-chunk scores populated.
     *
     * @param semantic non-null, may be empty; every element has a semantic score
     * @param keyword  non-null, may be empty; every element carries a raw keyword
     *                 relevance score from PostgreSQL
     * @return deduplicated, score-normalized candidates; never null
     */
    public List<SemanticSearchResult> merge(
            List<SemanticSearchResult> semantic,
            List<KeywordSearchResult> keyword) {
        Objects.requireNonNull(semantic, "semantic candidates must not be null");
        Objects.requireNonNull(keyword, "keyword candidates must not be null");

        Map<ChunkKey, KeywordSearchResult> keywordByKey = new LinkedHashMap<>();
        for (KeywordSearchResult hit : keyword) {
            keywordByKey.putIfAbsent(toKey(hit), hit);
        }

        double maxKeyword = keywordByKey.values().stream()
                .mapToDouble(KeywordSearchResult::keywordScore)
                .max()
                .orElse(0.0);
        double normalization = maxKeyword > 0.0 ? maxKeyword : 1.0;

        Map<ChunkKey, SemanticSearchResult> merged = new LinkedHashMap<>();

        for (SemanticSearchResult hit : semantic) {
            ChunkKey key = toKey(hit);
            KeywordSearchResult keywordHit = keywordByKey.remove(key);
            double normalizedKeyword = keywordHit == null
                    ? 0.0
                    : keywordHit.keywordScore() / normalization;
            merged.put(key, new SemanticSearchResult(
                    hit.documentId(),
                    hit.filename(),
                    hit.chunkIndex(),
                    hit.chunkText(),
                    hit.similarity(),
                    normalizedKeyword,
                    0.0));
        }

        for (Map.Entry<ChunkKey, KeywordSearchResult> entry : keywordByKey.entrySet()) {
            ChunkKey key = entry.getKey();
            KeywordSearchResult hit = entry.getValue();
            double normalizedKeyword = hit.keywordScore() / normalization;
            // Keyword-only: there is no cosine similarity, so similarity is ABSENT (null),
            // never the keyword relevance score. keywordScore carries the actual relevance.
            merged.put(key, new SemanticSearchResult(
                    key.documentId(),
                    hit.filename(),
                    key.chunkIndex(),
                    hit.chunkText(),
                    null,
                    normalizedKeyword,
                    0.0));
        }

        return new ArrayList<>(merged.values());
    }

    private ChunkKey toKey(SemanticSearchResult hit) {
        return new ChunkKey(hit.documentId(), hit.chunkIndex());
    }

    private ChunkKey toKey(KeywordSearchResult hit) {
        return new ChunkKey(hit.documentId(), hit.chunkIndex());
    }
}
