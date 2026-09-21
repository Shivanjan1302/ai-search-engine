package com.dronzer.aisearch.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.dronzer.aisearch.dto.SemanticSearchResult;

/**
 * The {@code hybrid ranker} component of Phase 2A.
 *
 * <p>Given the scores produced by the {@link HybridCandidateMerger}, this component
 * computes a single weighted relevance value per candidate and sorts the candidates
 * deterministically for downstream evidence selection.
 *
 * <p><strong>Ranking formula</strong>:
 *
 * <pre>
 * hybridScore = semanticWeight * similarity + keywordWeight * keywordScore
 * </pre>
 *
  * <p>where {@code similarity} is the cosine similarity from vector search (a keyword-only
 * chunk has no semantic score, represented as {@code null}, which contributes {@code 0.0}
 * to the weighted sum) and {@code keywordScore} is the {@code [0, 1]} keyword relevance
 * from the merger (0 for pure semantic hits).
 *
 * <p><strong>Defaults (initial, not tuned)</strong>: {@code semanticWeight = 0.7},
 * {@code keywordWeight = 0.3}. The vector signal dominates so that strong semantic
 * hits stay ahead of keyword-only backfill by default; the relative balance is tuned
 * via {@code app.rag.semantic-weight} / {@code app.rag.keyword-weight} using the
 * evaluation benchmark. Weights are normalized to a 0/1 mix so they remain
 * interpretable as relative importance even when configured with values that do not
 * sum to 1.0.
 *
 * <p><strong>Determinism</strong>: ties on {@code hybridScore} are broken by
 * {@code documentId} ascending then {@code chunkIndex} ascending, giving a total,
 * reproducible order.
 */
@Component
public class HybridRanker {

    private static final Comparator<SemanticSearchResult> SCORE_THEN_ID =
            Comparator.comparingDouble(SemanticSearchResult::hybridScore)
                    .reversed()
                    .thenComparing(SemanticSearchResult::documentId)
                    .thenComparing(SemanticSearchResult::chunkIndex);

    /**
     * Computes the weighted {@code hybridScore} for every candidate and returns them
     * in deterministic rank order.
     *
     * @param candidates       deduplicated, normalized candidates from the merger
     * @param semanticWeight weight applied to the semantic cosine score
     * @param keywordWeight  weight applied to the normalized keyword score
     * @return candidates sorted by descending hybrid relevance
     */
    public List<SemanticSearchResult> rank(
            List<SemanticSearchResult> candidates,
            double semanticWeight,
            double keywordWeight) {
        double weightSum = semanticWeight + keywordWeight;
        double effectiveSemantic = weightSum > 0.0 ? semanticWeight / weightSum : 0.5;
        double effectiveKeyword = weightSum > 0.0 ? keywordWeight / weightSum : 0.5;

        return candidates.stream()
                .map(candidate -> {
                    Double cosine = candidate.similarity();
                    double semanticTerm = cosine == null ? 0.0 : cosine;
                    return new SemanticSearchResult(
                            candidate.documentId(),
                            candidate.filename(),
                            candidate.chunkIndex(),
                            candidate.chunkText(),
                            cosine,
                            candidate.keywordScore(),
                            effectiveSemantic * semanticTerm
                                    + effectiveKeyword * candidate.keywordScore());
                })
                .sorted(SCORE_THEN_ID)
                .toList();
    }
}
