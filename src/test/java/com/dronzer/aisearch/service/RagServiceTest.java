package com.dronzer.aisearch.service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final AIClient aiClient = mock(AIClient.class);

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(documentService, aiClient);
    }

    @Test
    void rejectsBlankQuestions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ragService.askQuestion("  ", "user@example.test"))
                .withMessage("question must not be blank");

        verify(documentService, never()).searchSemantically(anyString(), eq(5), anyString());
    }

    @Test
    void returnsFallbackAnswerWhenNoSemanticResultsExist() {
        when(documentService.searchSemantically("What is RAG?", 5, "user@example.test"))
                .thenReturn(List.of());

        RagResponse response = ragService.askQuestion("What is RAG?", "user@example.test");

        assertThat(response.answer())
                .isEqualTo("I could not find relevant information in your documents.");
        assertThat(response.sources()).isEmpty();
        verify(aiClient, never()).generateAnswer(anyString());
    }

    @Test
    void generatesGroundedAnswerAndMapsSourcesForAuthenticatedUser() {
        SemanticSearchResult firstResult = new SemanticSearchResult(
                10L, "guide.pdf", 2, "RAG combines retrieval with generation.", 0.91);
        SemanticSearchResult secondResult = new SemanticSearchResult(
                11L, "notes.txt", 0, "Embeddings support semantic retrieval.", 0.84);
        when(documentService.searchSemantically("How does RAG work?", 5, "user@example.test"))
                .thenReturn(List.of(firstResult, secondResult));
        when(aiClient.generateAnswer(anyString())).thenReturn("It retrieves relevant chunks before generating an answer.");

        RagResponse response = ragService.askQuestion("How does RAG work?", "user@example.test");

        assertThat(response.answer())
                .isEqualTo("It retrieves relevant chunks before generating an answer.");
        assertThat(response.sources()).containsExactly(
                new com.dronzer.aisearch.dto.RagSource(10L, "guide.pdf", 2, 0.91),
                new com.dronzer.aisearch.dto.RagSource(11L, "notes.txt", 0, 0.84));
        verify(documentService).searchSemantically(
                "How does RAG work?", 5, "user@example.test");
        verify(aiClient).generateAnswer(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("ONLY the provided document context")
                        && prompt.contains("[Source 1: guide.pdf, chunk 2]")
                        && prompt.contains("RAG combines retrieval with generation.")
                        && prompt.contains("USER QUESTION:\nHow does RAG work?")));
    }
}
