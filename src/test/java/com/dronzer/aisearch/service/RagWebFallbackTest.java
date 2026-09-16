package com.dronzer.aisearch.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.dronzer.aisearch.exception.WebSearchUpstreamException;

class RagWebFallbackTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final AIClient aiClient = mock(AIClient.class);
    private final WebSearchClient webSearchClient = mock(WebSearchClient.class);
    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(documentService, aiClient, webSearchClient);
        ReflectionTestUtils.setField(ragService, "webResultLimit", 5);
        when(documentService.searchSemantically(anyString(), org.mockito.ArgumentMatchers.eq(20), anyString()))
                .thenReturn(List.of());
    }

    @Test
    void disabledFallbackPreservesInsufficientEvidenceBehavior() {
        RagResponse response = ragService.askQuestion("Unknown question", "user@example.test");

        assertThat(response.answer()).isEqualTo("I could not find relevant information in your documents.");
        assertThat(response.webSources()).isEmpty();
        assertThat(response.origin()).isEqualTo(RagOrigin.INSUFFICIENT_EVIDENCE);
        verify(webSearchClient, never()).search(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void enabledFallbackReturnsWebGroundedAnswerAndSources() {
        ReflectionTestUtils.setField(ragService, "webFallbackEnabled", true);
        List<WebSearchResult> webResults = List.of(
                new WebSearchResult("Example title", "https://example.com/article",
                        "Supported web evidence.", "example.com"));
        when(webSearchClient.search("What is documented online?", 5)).thenReturn(webResults);
        when(aiClient.generateAnswer(anyString())).thenReturn("Web-grounded answer.");

        RagResponse response = ragService.askQuestion("What is documented online?", "user@example.test");

        assertThat(response.answer()).isEqualTo("Web-grounded answer.");
        assertThat(response.sources()).isEmpty();
        assertThat(response.webSources()).containsExactlyElementsOf(webResults);
        assertThat(response.origin()).isEqualTo(RagOrigin.WEB);
        verify(aiClient).generateAnswer(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("ONLY the supplied web search evidence")
                        && prompt.contains("Supported web evidence.")
                        && prompt.contains("https://example.com/article")));
    }

    @Test
    void documentEvidencePreventsWebFallback() {
        when(documentService.searchSemantically("Document question", 20, "user@example.test"))
                .thenReturn(List.of(new SemanticSearchResult(
                        7L, "private.txt", 0, "Private evidence.", 0.90)));
        ReflectionTestUtils.setField(ragService, "webFallbackEnabled", true);
        when(aiClient.generateAnswer(anyString())).thenReturn("Document answer.");

        RagResponse response = ragService.askQuestion("Document question", "user@example.test");

        assertThat(response.origin()).isEqualTo(RagOrigin.DOCUMENTS);
        assertThat(response.sources()).hasSize(1);
        assertThat(response.webSources()).isEmpty();
        verify(webSearchClient, never()).search(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void providerFailureIsNotConvertedIntoAWebAnswer() {
        ReflectionTestUtils.setField(ragService, "webFallbackEnabled", true);
        when(webSearchClient.search(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new WebSearchUpstreamException());

        assertThatThrownBy(() -> ragService.askQuestion("Provider failure", "user@example.test"))
                .isInstanceOf(WebSearchUpstreamException.class)
                .hasMessage("Web search is temporarily unavailable");
        verify(aiClient, never()).generateAnswer(anyString());
    }
}