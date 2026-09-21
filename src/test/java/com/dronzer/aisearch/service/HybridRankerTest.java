package com.dronzer.aisearch.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.dronzer.aisearch.dto.SemanticSearchResult;

class HybridRankerTest {

    private final HybridRanker ranker = new HybridRanker();

    @Test
    void combinesScoresWithConfiguredWeightsAndBreaksTiesDeterministically() {
        SemanticSearchResult first = new SemanticSearchResult(20L, "b.txt", 0, "B.", 0.80, 0.0, 0.0);
        SemanticSearchResult second = new SemanticSearchResult(10L, "a.txt", 0, "A.", 0.80, 0.0, 0.0);
        SemanticSearchResult keywordBoosted = new SemanticSearchResult(30L, "c.txt", 0, "C.", 0.60, 1.0, 0.0);

        List<SemanticSearchResult> ranked = ranker.rank(List.of(first, second, keywordBoosted), 0.7, 0.3);

        assertThat(ranked)
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("30:0", "10:0", "20:0");
        assertThat(ranked.get(0).hybridScore()).isEqualTo(0.7 * 0.60 + 0.3 * 1.0);
        assertThat(ranker.rank(List.of(first, second, keywordBoosted), 0.7, 0.3))
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("30:0", "10:0", "20:0");
    }
}
