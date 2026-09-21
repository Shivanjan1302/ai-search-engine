package com.dronzer.aisearch.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;

class HybridRagServiceTest {

    private HybridRetrievalService hybridRetrievalService;
    private AIClient aiClient;
    private WebSearchClient webSearchClient;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        hybridRetrievalService = mock(HybridRetrievalService.class);
        aiClient = mock(AIClient.class);
        webSearchClient = mock(WebSearchClient.class);
        ragService = new RagService(hybridRetrievalService, aiClient, webSearchClient);
        ReflectionTestUtils.setField(ragService, "retrievalCandidateLimit", 20);
        ReflectionTestUtils.setField(ragService, "finalContextLimit", 6);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.65);
        ReflectionTestUtils.setField(ragService, "keywordThreshold", 0.5);
        ReflectionTestUtils.setField(ragService, "maxChunksPerDocument", 3);
        ReflectionTestUtils.setField(ragService, "webFallbackEnabled", false);
    }

    @Test
    void keywordOnlyCandidateCanSurfaceWhenSemanticSearchMisses() {
        // True keyword-only candidate: no cosine similarity (null), full normalized
        // keyword relevance. Post-rank hybridScore is 0.7 * 0.0 + 0.3 * 1.0 = 0.3,
        // which is below the 0.65 semantic bar — the candidate must be admitted on
        // keyword relevance, not rejected for lacking a semantic score.
        SemanticSearchResult keywordOnly = new SemanticSearchResult(
                10L, "runbook.txt", 0, "Deployment fails with ERR_DB_CONN_42X.", null, 1.0, 0.3);
        when(hybridRetrievalService.retrieve("ERR_DB_CONN_42X", 20, "user@example.test"))
                .thenReturn(List.of(keywordOnly));
        when(aiClient.generateAnswer(anyString())).thenReturn("Check ERR_DB_CONN_42X.");

        RagResponse response = ragService.askQuestion("ERR_DB_CONN_42X", "user@example.test");

        assertThat(response.origin()).isEqualTo(RagOrigin.DOCUMENTS);
        assertThat(response.sources())
                .extracting(source -> source.documentId() + ":" + source.chunkIndex())
                .containsExactly("10:0");
        // Keyword-only chunk reports genuine absence of semantic similarity (0.0),
        // never the keyword relevance; keywordScore and hybridScore are preserved.
        assertThat(response.sources().get(0).similarity()).isEqualTo(0.0);
        assertThat(response.sources().get(0).keywordScore()).isEqualTo(1.0);
        assertThat(response.sources().get(0).hybridScore()).isEqualTo(0.3);
    }

    @Test
    void deduplicatedHybridCandidatesRespectPerDocumentAndContextLimits() {
        List<SemanticSearchResult> merged = List.of(
                new SemanticSearchResult(20L, "other.txt", 0, "Other.", 0.80, 0.0, 0.56),
                new SemanticSearchResult(10L, "guide.txt", 0, "First.", 0.95, 0.0, 0.665),
                new SemanticSearchResult(10L, "guide.txt", 1, "Duplicate.", 0.90, 0.0, 0.63),
                new SemanticSearchResult(10L, "guide.txt", 2, "Third.", 0.90, 0.0, 0.63),
                new SemanticSearchResult(10L, "guide.txt", 3, "Fourth.", 0.89, 0.0, 0.623));
        when(hybridRetrievalService.retrieve("How does RAG work?", 20, "user@example.test"))
                .thenReturn(merged);
        when(aiClient.generateAnswer(anyString())).thenReturn("Grounded answer.");

        RagResponse response = ragService.askQuestion("How does RAG work?", "user@example.test");

        assertThat(response.sources())
                .extracting(source -> source.documentId() + ":" + source.chunkIndex())
                .containsExactly("10:0", "10:1", "10:2", "20:0");
    }

    @Test
    void semanticThresholdStillGatesCosineWhileKeywordPathUsesKeywordRelevance() {
        // Case B: semantic-only candidate just below the 0.65 cosine bar → rejected.
        SemanticSearchResult weakSemantic = new SemanticSearchResult(
                20L, "notes.txt", 0, "Weak semantic match.", 0.64, 0.0, 0.64);
        // Case C: semantic-only candidate above the bar → eligible.
        SemanticSearchResult strongSemantic = new SemanticSearchResult(
                30L, "guide.txt", 0, "Strong semantic match.", 0.80, 0.0, 0.80);
        // Case A: true keyword-only candidate (similarity null). hybridScore 0.30 is
        // below the 0.65 semantic bar, but keywordScore 1.0 clears the keyword
        // threshold → must not be rejected merely for lacking semantic similarity.
        SemanticSearchResult keywordOnly = new SemanticSearchResult(
                10L, "runbook.txt", 0, "Deployment fails with ERR_DB_CONN_42X.", null, 1.0, 0.3);
        // Case D: candidate matching both signals → eligible, ranked by hybridScore.
        SemanticSearchResult bothSignals = new SemanticSearchResult(
                40L, "manual.txt", 0, "Hybrid match.", 0.80, 1.0, 0.86);
        when(hybridRetrievalService.retrieve("mixed query", 20, "user@example.test"))
                .thenReturn(List.of(weakSemantic, strongSemantic, keywordOnly, bothSignals));
        when(aiClient.generateAnswer(anyString())).thenReturn("Grounded answer.");

        RagResponse response = ragService.askQuestion("mixed query", "user@example.test");

        // Weak semantic candidate (0.64 < 0.65) is excluded; the other three survive,
        // ranked by hybridScore descending: bothSignals (0.86), strongSemantic
        // (0.80 seed), keywordOnly (0.30).
        assertThat(response.origin()).isEqualTo(RagOrigin.DOCUMENTS);
        assertThat(response.sources())
                .extracting(source -> source.documentId() + ":" + source.chunkIndex())
                .containsExactly("40:0", "30:0", "10:0");
        // Both-signals candidate preserves every score.
        assertThat(response.sources().get(0).similarity()).isEqualTo(0.80);
        assertThat(response.sources().get(0).keywordScore()).isEqualTo(1.0);
        assertThat(response.sources().get(0).hybridScore()).isEqualTo(0.86);
        // Keyword-only candidate reports absence of semantic similarity, never the
        // keyword relevance, while keywordScore and hybridScore are preserved.
        assertThat(response.sources().get(2).similarity()).isEqualTo(0.0);
        assertThat(response.sources().get(2).keywordScore()).isEqualTo(1.0);
        assertThat(response.sources().get(2).hybridScore()).isEqualTo(0.3);
    }
}
