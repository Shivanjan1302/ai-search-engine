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

        verify(documentService, never()).searchSemantically(anyString(), eq(20), anyString());
    }

    @Test
    void returnsFallbackAnswerWhenNoSemanticResultsExist() {
        when(documentService.searchSemantically("What is RAG?", 20, "user@example.test"))
                .thenReturn(List.of());

        RagResponse response = ragService.askQuestion("What is RAG?", "user@example.test");

        assertThat(response.answer())
                .isEqualTo("I could not find relevant information in your documents.");
        assertThat(response.sources()).isEmpty();
        verify(aiClient, never()).generateAnswer(anyString());
    }

    @Test
    void rejectsWeakResultsAndKeepsStrongResults() {
        SemanticSearchResult strong = result(10L, 0, 0.80, "Strong evidence.");
        SemanticSearchResult weak = result(20L, 0, 0.64, "Weak evidence.");
        when(documentService.searchSemantically("What is RAG?", 20, "user@example.test"))
                .thenReturn(List.of(weak, strong));
        when(aiClient.generateAnswer(anyString())).thenReturn("Grounded answer.");

        RagResponse response = ragService.askQuestion("What is RAG?", "user@example.test");

        assertThat(response.sources())
                .extracting(com.dronzer.aisearch.dto.RagSource::documentId)
                .containsExactly(10L);
        verify(documentService).searchSemantically("What is RAG?", 20, "user@example.test");
    }

    @Test
    void returnsFallbackWhenNoResultSurvivesTheThreshold() {
        when(documentService.searchSemantically("What is RAG?", 20, "user@example.test"))
                .thenReturn(List.of(result(10L, 0, 0.64, "Weak evidence.")));

        RagResponse response = ragService.askQuestion("What is RAG?", "user@example.test");

        assertThat(response.answer())
                .isEqualTo("I could not find relevant information in your documents.");
        assertThat(response.sources()).isEmpty();
        verify(aiClient, never()).generateAnswer(anyString());
    }

    @Test
    void removesDuplicatesCapsDocumentsAndOrdersTiesDeterministically() {
        List<SemanticSearchResult> results = List.of(
                result(20L, 0, 0.80, "Other document."),
                result(10L, 3, 0.90, "Fourth chunk."),
                result(10L, 1, 0.90, "Second chunk."),
                result(10L, 0, 0.95, "First chunk."),
                result(10L, 1, 0.85, "Duplicate chunk."),
                result(10L, 2, 0.90, "Third chunk."),
                result(10L, 4, 0.89, "Capped chunk."));
        when(documentService.searchSemantically("How does RAG work?", 20, "user@example.test"))
                .thenReturn(results);
        when(aiClient.generateAnswer(anyString())).thenReturn("Grounded answer.");

        RagResponse response = ragService.askQuestion("How does RAG work?", "user@example.test");

        assertThat(response.sources())
                .extracting(source -> source.documentId() + ":" + source.chunkIndex())
                .containsExactly("10:0", "10:1", "10:2", "20:0");
    }

    @Test
    void limitsFinalContextAfterApplyingDocumentCaps() {
        List<SemanticSearchResult> results = List.of(
                result(10L, 0, 0.95, "A0"), result(10L, 1, 0.94, "A1"),
                result(10L, 2, 0.93, "A2"), result(10L, 3, 0.92, "A3"),
                result(20L, 0, 0.91, "B0"), result(20L, 1, 0.90, "B1"),
                result(30L, 0, 0.89, "C0"));
        when(documentService.searchSemantically("How does RAG work?", 20, "user@example.test"))
                .thenReturn(results);
        when(aiClient.generateAnswer(anyString())).thenReturn("Grounded answer.");

        RagResponse response = ragService.askQuestion("How does RAG work?", "user@example.test");

        assertThat(response.sources()).hasSize(6);
        assertThat(response.sources())
                .extracting(source -> source.documentId() + ":" + source.chunkIndex())
                .containsExactly("10:0", "10:1", "10:2", "20:0", "20:1", "30:0");
    }

    @Test
    void generatesGroundedAnswerAndMapsSourcesForAuthenticatedUser() {
        SemanticSearchResult firstResult = new SemanticSearchResult(
                10L, "guide.pdf", 2, "RAG combines retrieval with generation.", 0.91);
        SemanticSearchResult secondResult = new SemanticSearchResult(
                11L, "notes.txt", 0, "Embeddings support semantic retrieval.", 0.84);
        when(documentService.searchSemantically("How does RAG work?", 20, "user@example.test"))
                .thenReturn(List.of(firstResult, secondResult));
        when(aiClient.generateAnswer(anyString())).thenReturn("It retrieves relevant chunks before generating an answer.");

        RagResponse response = ragService.askQuestion("How does RAG work?", "user@example.test");

        assertThat(response.answer())
                .isEqualTo("It retrieves relevant chunks before generating an answer.");
        assertThat(response.sources()).containsExactly(
                new com.dronzer.aisearch.dto.RagSource(10L, "guide.pdf", 2, 0.91),
                new com.dronzer.aisearch.dto.RagSource(11L, "notes.txt", 0, 0.84));
        verify(documentService).searchSemantically(
                "How does RAG work?", 20, "user@example.test");
        verify(aiClient).generateAnswer(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("ONLY the provided document context")
                        && prompt.contains("[Source 1: guide.pdf, chunk 2]")
                        && prompt.contains("RAG combines retrieval with generation.")
                        && prompt.contains("USER QUESTION:\nHow does RAG work?")));
    }

        private SemanticSearchResult result(Long documentId, int chunkIndex, double similarity, String text) {
                return new SemanticSearchResult(documentId, "document-" + documentId + ".txt", chunkIndex, text, similarity);
        }
}
