package com.dronzer.aisearch.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.dronzer.aisearch.dto.KeywordSearchResult;
import com.dronzer.aisearch.dto.SemanticSearchResult;

class HybridCandidateMergerTest {

    private final HybridCandidateMerger merger = new HybridCandidateMerger();

    @Test
    void keepsSemanticScoreAndNormalizesSharedKeywordHit() {
        SemanticSearchResult semantic = new SemanticSearchResult(10L, "a.txt", 0, "text", 0.80);
        KeywordSearchResult keyword = new KeywordSearchResult(10L, "a.txt", 0, "text", 2.0);

        List<SemanticSearchResult> merged = merger.merge(List.of(semantic), List.of(keyword));

        assertThat(merged).hasSize(1);
        // A chunk returned by both searches keeps its real cosine similarity ...
        assertThat(merged.get(0).similarity()).isEqualTo(0.80);
        // ... and the (normalized) keyword relevance is attached, not substituted in.
        assertThat(merged.get(0).keywordScore()).isEqualTo(1.0);
    }

    @Test
    void keywordOnlyHitsHaveNoSemanticScoreAndKeepNormalizedKeywordRelevance() {
        KeywordSearchResult first = new KeywordSearchResult(10L, "a.txt", 0, "first", 2.0);
        KeywordSearchResult second = new KeywordSearchResult(10L, "a.txt", 1, "second", 1.0);

        List<SemanticSearchResult> merged = merger.merge(List.of(), List.of(first, second));

        assertThat(merged)
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("10:0", "10:1");
        // Keyword-only chunks carry NO semantic similarity (null), so the score cannot be
        // confused with the keyword relevance below it.
        assertThat(merged.get(0).similarity()).isNull();
        assertThat(merged.get(1).similarity()).isNull();
        assertThat(merged.get(0).keywordScore()).isEqualTo(1.0);
        assertThat(merged.get(1).keywordScore()).isEqualTo(0.5);
    }

    @Test
    void chunkPresentInBothSearchesIsNotDuplicated() {
        SemanticSearchResult semantic = new SemanticSearchResult(10L, "a.txt", 0, "text", 0.70);
        KeywordSearchResult keyword = new KeywordSearchResult(10L, "a.txt", 0, "text", 5.0);

        List<SemanticSearchResult> merged = merger.merge(List.of(semantic), List.of(keyword));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).documentId()).isEqualTo(10L);
        assertThat(merged.get(0).chunkIndex()).isEqualTo(0);
        assertThat(merged.get(0).keywordScore()).isEqualTo(1.0);
    }
}
