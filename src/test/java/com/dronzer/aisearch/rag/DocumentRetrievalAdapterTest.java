package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRetrievalAdapterTest {

    @Test
    void fromResults_emptyList_returnsEmptyList() {
        List<DocumentEvidence> evidence = DocumentRetrievalAdapter.fromResults(List.of());
        assertTrue(evidence.isEmpty());
    }

    @Test
    void fromResults_nullList_returnsEmptyList() {
        List<DocumentEvidence> evidence = DocumentRetrievalAdapter.fromResults(null);
        assertTrue(evidence.isEmpty());
    }

    @Test
    void fromResults_singleResult_convertsCorrectly() {
        SemanticSearchResult result = new SemanticSearchResult(
                123L,
                "policy.pdf",
                2,
                "Remote work is allowed",
                0.85,
                0.7,
                0.775
        );

        DocumentEvidence evidence = DocumentRetrievalAdapter.fromResult(result);

        assertEquals(123L, evidence.documentId());
        assertEquals("policy.pdf", evidence.filename());
        assertEquals(2, evidence.chunkIndex());
        assertEquals("Remote work is allowed", evidence.content());
        assertEquals(0.85, evidence.similarity());
        assertEquals(0.7, evidence.keywordScore());
        assertEquals(0.775, evidence.hybridScore());
        assertEquals(KnowledgeSource.DOCUMENT, evidence.source());
        assertEquals("123::2", evidence.id());
        assertEquals("policy.pdf, chunk 2", evidence.provenance());
        assertEquals(0.775, evidence.score());
        assertEquals("semantic", evidence.retrievalMethod());
    }

    @Test
    void fromResults_mixOfResults_convertsAll() {
        SemanticSearchResult result1 = new SemanticSearchResult(
                1L, "doc1.txt", 0, "Content 1", 0.9, 0.0, 0.9);
        SemanticSearchResult result2 = new SemanticSearchResult(
                2L, "doc2.txt", 1, "Content 2", null, 0.8, 0.4);

        List<DocumentEvidence> evidence = DocumentRetrievalAdapter.fromResults(List.of(result1, result2));

        assertEquals(2, evidence.size());
        assertEquals("doc1.txt", evidence.get(0).filename());
        assertEquals("doc2.txt", evidence.get(1).filename());
        assertEquals(KnowledgeSource.DOCUMENT, evidence.get(0).source());
        assertEquals(KnowledgeSource.DOCUMENT, evidence.get(1).source());
    }

    @Test
    void fromResult_keywordOnlyResult_usesKeywordScore() {
        SemanticSearchResult result = new SemanticSearchResult(
                42L, "keyword.pdf", 5, "Keyword hit", null, 0.6, 0.3);

        DocumentEvidence evidence = DocumentRetrievalAdapter.fromResult(result);

        assertNull(evidence.similarity());
        assertEquals(0.6, evidence.keywordScore());
        assertEquals(0.3, evidence.hybridScore());
        // retrievalScore is keywordScore for keyword-only results
        assertEquals(0.6, evidence.retrievalScore());
        // score() returns hybridScore when > 0
        assertEquals(0.3, evidence.score());
    }

    @Test
    void fromResult_withSimilarityOnly_usesSimilarityAsRetrievalScore() {
        SemanticSearchResult result = new SemanticSearchResult(
                99L, "semantic.pdf", 0, "Vector hit", 0.75, 0.0, 0.75);

        DocumentEvidence evidence = DocumentRetrievalAdapter.fromResult(result);

        assertEquals(0.75, evidence.retrievalScore());
    }

    @Test
    void fromResult_nullResult_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentRetrievalAdapter.fromResult(null));
    }

    @Test
    void score_usesHybridScoreWhenPositive() {
        SemanticSearchResult result = new SemanticSearchResult(
                1L, "doc.txt", 0, "Text", 0.5, 0.3, 0.8);

        DocumentEvidence evidence = DocumentRetrievalAdapter.fromResult(result);

        assertEquals(0.8, evidence.retrievalScore());
        assertEquals(0.8, evidence.score());
    }

    @Test
    void score_usesSimilarityWhenHybridZero() {
        SemanticSearchResult result = new SemanticSearchResult(
                1L, "doc.txt", 0, "Text", 0.5, 0.0, 0.0);

        DocumentEvidence evidence = DocumentRetrievalAdapter.fromResult(result);

        assertEquals(0.5, evidence.retrievalScore());
    }

    @Test
    void score_usesKeywordWhenEverythingZero() {
        SemanticSearchResult result = new SemanticSearchResult(
                1L, "doc.txt", 0, "Text", null, 0.4, 0.0);

        DocumentEvidence evidence = DocumentRetrievalAdapter.fromResult(result);

        assertEquals(0.4, evidence.retrievalScore());
    }

    @Test
    void equals_basedOnId() {
        SemanticSearchResult r1 = new SemanticSearchResult(1L, "a.txt", 0, "X", 0.5, 0.0, 0.5);
        SemanticSearchResult r2 = new SemanticSearchResult(1L, "b.txt", 0, "Y", 0.6, 0.4, 0.5);

        DocumentEvidence e1 = DocumentRetrievalAdapter.fromResult(r1);
        DocumentEvidence e2 = DocumentRetrievalAdapter.fromResult(r2);

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void differentIds_notEqual() {
        SemanticSearchResult r1 = new SemanticSearchResult(1L, "a.txt", 0, "X", 0.5, 0.0, 0.5);
        SemanticSearchResult r2 = new SemanticSearchResult(2L, "b.txt", 0, "X", 0.5, 0.0, 0.5);

        DocumentEvidence e1 = DocumentRetrievalAdapter.fromResult(r1);
        DocumentEvidence e2 = DocumentRetrievalAdapter.fromResult(r2);

        assertNotEquals(e1, e2);
    }
}
