package com.dronzer.aisearch.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.dronzer.aisearch.dto.KeywordSearchResult;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.KeywordSearchRepository;
import com.dronzer.aisearch.repository.UserRepository;

class HybridRetrievalServiceTest {

    private DocumentService documentService;
    private UserRepository userRepository;
    private KeywordSearchRepository keywordSearchRepository;
    private HybridRetrievalService hybridService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        userRepository = mock(UserRepository.class);
        keywordSearchRepository = mock(KeywordSearchRepository.class);
        hybridService = new HybridRetrievalService(
                documentService, userRepository, keywordSearchRepository,
                new HybridCandidateMerger(), new HybridRanker(),
                true, 0.7, 0.3);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByEmail("user@example.test")).thenReturn(Optional.of(user));
    }

    @Test
    void exactKeywordMatchRetrievesChunkSemanticSearchMisses() {
        when(documentService.searchSemantically("ERR_DB_CONN_42X", 20, "user@example.test"))
                .thenReturn(List.of());
        when(keywordSearchRepository.findMatches(eq(1L), eq("ERR_DB_CONN_42X"), eq(20)))
                .thenReturn(List.of(keyword(10L, 0, "Deployment fails with ERR_DB_CONN_42X.", 1.0)));

        List<SemanticSearchResult> results = hybridService.retrieve("ERR_DB_CONN_42X", 20, "user@example.test");

        assertThat(results)
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("10:0");
    }

    @Test
    void semanticOnlyMatchStillWorksWhenKeywordSearchMisses() {
        when(documentService.searchSemantically("What is RAG?", 20, "user@example.test"))
                .thenReturn(List.of(semantic(10L, 0, 0.90, "RAG combines retrieval.")));
        when(keywordSearchRepository.findMatches(eq(1L), eq("What is RAG?"), eq(20)))
                .thenReturn(List.of());

        List<SemanticSearchResult> results = hybridService.retrieve("What is RAG?", 20, "user@example.test");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).keywordScore()).isZero();
    }

    @Test
    void chunkReturnedByBothSearchesIsDeduplicated() {
        when(documentService.searchSemantically("invoice INV-2024-9917", 20, "user@example.test"))
                .thenReturn(List.of(semantic(10L, 0, 0.80, "Invoice INV-2024-9917 is overdue.")));
        when(keywordSearchRepository.findMatches(eq(1L), eq("invoice INV-2024-9917"), eq(20)))
                .thenReturn(List.of(keyword(10L, 0, "Invoice INV-2024-9917 is overdue.", 2.0)));

        List<SemanticSearchResult> results = hybridService.retrieve("invoice INV-2024-9917", 20, "user@example.test");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).similarity()).isEqualTo(0.80);
        assertThat(results.get(0).keywordScore()).isEqualTo(1.0);
    }

    @Test
    void keywordRetrievalOnlySeesTheAuthenticatedUsersDocuments() {
        when(documentService.searchSemantically("plans", 20, "user@example.test"))
                .thenReturn(List.of());
        when(keywordSearchRepository.findMatches(eq(1L), eq("plans"), eq(20)))
                .thenReturn(List.of());

        hybridService.retrieve("plans", 20, "user@example.test");

        verify(keywordSearchRepository).findMatches(eq(1L), eq("plans"), eq(20));
        verify(keywordSearchRepository, never()).findMatches(eq(2L), anyString(), anyInt());
    }

    @Test
    void hybridRankingIsDeterministicForTiedScores() {
        when(documentService.searchSemantically("release", 20, "user@example.test"))
                .thenReturn(List.of(semantic(20L, 0, 0.80, "B."), semantic(10L, 0, 0.80, "A.")));
        when(keywordSearchRepository.findMatches(eq(1L), eq("release"), eq(20)))
                .thenReturn(List.of());

        List<SemanticSearchResult> results = hybridService.retrieve("release", 20, "user@example.test");

        assertThat(results)
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("10:0", "20:0");
        assertThat(hybridService.retrieve("release", 20, "user@example.test"))
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("10:0", "20:0");
    }

    @Test
    void keywordSearchCanBeDisabledToRestoreVectorOnlyBehavior() {
        HybridRetrievalService disabled = new HybridRetrievalService(
                documentService, userRepository, keywordSearchRepository,
                new HybridCandidateMerger(), new HybridRanker(),
                false, 1.0, 0.0);
        when(documentService.searchSemantically("What is RAG?", 20, "user@example.test"))
                .thenReturn(List.of(semantic(10L, 0, 0.90, "RAG combines retrieval.")));

        List<SemanticSearchResult> results = disabled.retrieve("What is RAG?", 20, "user@example.test");

        assertThat(results)
                .extracting(result -> result.documentId() + ":" + result.chunkIndex())
                .containsExactly("10:0");
        verify(keywordSearchRepository, never()).findMatches(anyLong(), anyString(), anyInt());
    }

    private static SemanticSearchResult semantic(Long documentId, int chunkIndex, double similarity, String text) {
        return new SemanticSearchResult(documentId, "document-" + documentId + ".txt", chunkIndex, text, similarity);
    }

    private static KeywordSearchResult keyword(Long documentId, int chunkIndex, String text, double score) {
        return new KeywordSearchResult(documentId, "document-" + documentId + ".txt", chunkIndex, text, score);
    }
}
