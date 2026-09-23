package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.dto.WebSearchResult;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebRetrievalAdapterTest {

    @Test
    void fromResults_emptyList_returnsEmptyList() {
        List<WebEvidence> evidence = WebRetrievalAdapter.fromResults(List.of());
        assertTrue(evidence.isEmpty());
    }

    @Test
    void fromResults_nullList_returnsEmptyList() {
        List<WebEvidence> evidence = WebRetrievalAdapter.fromResults(null);
        assertTrue(evidence.isEmpty());
    }

    @Test
    void fromResults_singleResult_convertsCorrectly() {
        WebSearchResult result = new WebSearchResult(
                "Example Title",
                "https://example.com/article",
                "This is a snippet from the article.",
                "Example Publisher"
        );

        WebEvidence evidence = WebRetrievalAdapter.fromResult(result);

        assertEquals("https://example.com/article", evidence.url());
        assertEquals("Example Title", evidence.title());
        assertEquals("Example Publisher", evidence.publisher());
        assertEquals("This is a snippet from the article.", evidence.content());
        assertEquals(KnowledgeSource.WEB, evidence.source());
        assertEquals("https://example.com/article", evidence.id());
        assertEquals("Example Title, Example Publisher", evidence.provenance());
        assertNull(evidence.retrievalScore());
        assertEquals("web", evidence.retrievalMethod());
    }

    @Test
    void fromResults_multipleResults_convertsAll() {
        WebSearchResult result1 = new WebSearchResult(
                "Title 1", "https://example.com/1", "Snippet 1", "Publisher 1");
        WebSearchResult result2 = new WebSearchResult(
                "Title 2", "https://example.com/2", "Snippet 2", "Publisher 2");

        List<WebEvidence> evidence = WebRetrievalAdapter.fromResults(List.of(result1, result2));

        assertEquals(2, evidence.size());
        assertEquals("Title 1", evidence.get(0).title());
        assertEquals("Title 2", evidence.get(1).title());
        assertEquals(KnowledgeSource.WEB, evidence.get(0).source());
        assertEquals(KnowledgeSource.WEB, evidence.get(1).source());
    }

    @Test
    void fromResult_withFullWebResult_convertsAllFields() {
        WebSearchResult result = new WebSearchResult(
                "Full Title",
                "https://example.com/path/article",
                "Full snippet text",
                "Full Publisher",
                "example.com",
                "/path/article",
                "example.com › path › article",
                "2024-01-15"
        );

        WebEvidence evidence = WebRetrievalAdapter.fromResult(result);

        assertEquals("https://example.com/path/article", evidence.url());
        assertEquals("Full Title", evidence.title());
        assertEquals("Full Publisher", evidence.publisher());
        assertEquals("Full snippet text", evidence.content());
        // Retrieval score and method are not mapped from WebSearchResult
        assertNull(evidence.retrievalScore());
        assertEquals("web", evidence.retrievalMethod());
    }

    @Test
    void fromResult_nullResult_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> WebRetrievalAdapter.fromResult(null));
    }

    @Test
    void score_isNullWhenNoRetrievalScoreSet() {
        WebSearchResult result = new WebSearchResult(
                "Title", "https://example.com", "Snippet", "Publisher");

        WebEvidence evidence = WebRetrievalAdapter.fromResult(result);

        assertNull(evidence.score());
    }

    @Test
    void equals_basedOnUrl() {
        WebSearchResult r1 = new WebSearchResult("Title 1", "https://example.com", "Snippet 1", "Pub 1");
        WebSearchResult r2 = new WebSearchResult("Title 2", "https://example.com", "Snippet 2", "Pub 2");

        WebEvidence e1 = WebRetrievalAdapter.fromResult(r1);
        WebEvidence e2 = WebRetrievalAdapter.fromResult(r2);

        assertEquals(e1, e2);
    }

    @Test
    void differentUrls_notEqual() {
        WebSearchResult r1 = new WebSearchResult("Title 1", "https://example.com/1", "Snippet 1", "Pub 1");
        WebSearchResult r2 = new WebSearchResult("Title 2", "https://example.com/2", "Snippet 2", "Pub 2");

        WebEvidence e1 = WebRetrievalAdapter.fromResult(r1);
        WebEvidence e2 = WebRetrievalAdapter.fromResult(r2);

        assertNotEquals(e1, e2);
    }

    @Test
    void provenances_areHumanReadable() {
        WebSearchResult result = new WebSearchResult(
                "How to implement RAG",
                "https://ai.example.com/blog/rag-guide",
                "A comprehensive guide to retrieval-augmented generation.",
                "AI Research Lab"
        );

        WebEvidence evidence = WebRetrievalAdapter.fromResult(result);

        assertEquals("How to implement RAG, AI Research Lab", evidence.provenance());
    }
}
