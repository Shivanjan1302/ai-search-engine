package com.dronzer.aisearch.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.dto.WebSearchResult;

class HybridWebFallbackTest {

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
        ReflectionTestUtils.setField(ragService, "maxChunksPerDocument", 3);
        ReflectionTestUtils.setField(ragService, "webFallbackEnabled", true);
        ReflectionTestUtils.setField(ragService, "webResultLimit", 5);
    }

    @Test
    void hybridMissesStillFallBackToWebSearch() {
        when(hybridRetrievalService.retrieve(anyString(), eq(20), anyString()))
                .thenReturn(List.of());
        List<WebSearchResult> webResults = List.of(
                new WebSearchResult("Example title", "https://example.com/article",
                        "Supported web evidence.", "example.com"));
        when(webSearchClient.search("What is documented online?", 5)).thenReturn(webResults);
        when(aiClient.generateAnswer(anyString())).thenReturn("Web-grounded answer.");

        RagResponse response = ragService.askQuestion("What is documented online?", "user@example.test");

        assertThat(response.origin()).isEqualTo(RagOrigin.WEB);
        assertThat(response.webSources()).containsExactlyElementsOf(webResults);
        verify(aiClient).generateAnswer(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("ONLY the supplied web search evidence")));
    }

    @Test
    void hybridEvidenceStillPreventsWebFallback() {
        SemanticSearchResult keywordHit = new SemanticSearchResult(
                7L, "private.txt", 0, "Private evidence ERR_DB_CONN_42X.", 1.0, 1.0, 1.0);
        when(hybridRetrievalService.retrieve(eq("Private question"), eq(20), eq("user@example.test")))
                .thenReturn(List.of(keywordHit));
        when(aiClient.generateAnswer(anyString())).thenReturn("Document answer.");

        RagResponse response = ragService.askQuestion("Private question", "user@example.test");

        assertThat(response.origin()).isEqualTo(RagOrigin.DOCUMENTS);
        verify(webSearchClient, never())
                .search(anyString(), anyInt());
    }
}
