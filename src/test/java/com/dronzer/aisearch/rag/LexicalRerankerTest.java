package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for {@link LexicalReranker}.
 *
 * <p>Pure in-memory evidence only: no real Tavily/Gemini calls, no PostgreSQL, no
 * Spring context. Mockito is used only to simulate foreign {@link Evidence}
 * implementations at the contract boundary.</p>
 */
class LexicalRerankerTest {

    /** Tokenizes to the terms {vpn, setup, guide} after stopword removal. */
    private static final String QUERY = "vpn setup guide";

    private final LexicalReranker reranker = new LexicalReranker();

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static DocumentEvidence doc(long documentId, int chunkIndex, String content) {
        return new DocumentEvidence(documentId, "handbook.pdf", chunkIndex, content,
                0.91, 0.0, 0.85, 0.85, "hybrid");
    }

    private static WebEvidence web(String url, String content) {
        return new WebEvidence(url, "Example Title", "Example Publisher", content, null, "web");
    }

    // ------------------------------------------------------------------
    // Normal reranking
    // ------------------------------------------------------------------

    @Test
    void rerank_documentEvidence_ordersByQueryTermCoverage() {
        Evidence low = doc(1, 0, "Quarterly financial results and outlook");
        Evidence mid = doc(2, 0, "Configure the VPN client for remote access");
        Evidence high = doc(3, 0, "VPN setup guide for new employees");

        RerankResult result = reranker.rerank(QUERY, List.of(low, mid, high), null);

        assertEquals(3, result.ranked().size());
        assertSame(high, result.ranked().get(0));
        assertSame(mid, result.ranked().get(1));
        assertSame(low, result.ranked().get(2));
    }

    @Test
    void rerank_webEvidence_ordersByQueryTermCoverage() {
        Evidence low = web("https://example.com/weather", "Sailing weather forecast");
        Evidence mid = web("https://example.com/remote", "Remote access without VPN tooling");
        Evidence high = web("https://example.com/guide", "VPN setup guide");

        RerankResult result = reranker.rerank(QUERY, List.of(low, mid, high), null);

        assertEquals(3, result.ranked().size());
        assertSame(high, result.ranked().get(0));
        assertSame(mid, result.ranked().get(1));
        assertSame(low, result.ranked().get(2));
    }

    @Test
    void rerank_mixedEvidence_ranksDocumentAndWebTogetherByCoverage() {
        Evidence document = doc(7, 2, "Employees receive twenty days of annual leave per year");
        Evidence weakWeb = web("https://example.com/travel", "Company travel expenses overview");
        Evidence strongWeb = web("https://example.com/leave", "Annual leave policy explained");

        RerankResult result =
                reranker.rerank("annual leave policy", List.of(document, weakWeb, strongWeb), null);

        assertSame(strongWeb, result.ranked().get(0));
        assertSame(document, result.ranked().get(1));
        assertSame(weakWeb, result.ranked().get(2));
        // Source identity survives the mixed ranking.
        assertEquals(KnowledgeSource.WEB, result.ranked().get(0).source());
        assertEquals(KnowledgeSource.DOCUMENT, result.ranked().get(1).source());
        assertEquals(KnowledgeSource.WEB, result.ranked().get(2).source());
    }

    // ------------------------------------------------------------------
    // Determinism and ties
    // ------------------------------------------------------------------

    @Test
    void rerank_isDeterministic_repeatedInvocationsProduceIdenticalResult() {
        List<Evidence> candidates = List.of(
                doc(1, 0, "vpn"),
                web("https://example.com/a", "guide setup notes"),
                doc(2, 0, "nothing relevant"),
                web("https://example.com/b", "vpn guide"),
                doc(3, 0, "setup"));

        RerankResult first = reranker.rerank(QUERY, candidates, null);
        RerankResult second = reranker.rerank(QUERY, candidates, null);
        RerankResult third = reranker.rerank(QUERY, candidates, null);

        assertEquals(first, second);
        assertEquals(second, third);
    }

    @Test
    void rerank_tiedCoverage_keepsInitialRetrievalOrder() {
        Evidence tieA = doc(1, 0, "alpha one");
        Evidence tieB = web("https://example.com/b", "beta two");
        Evidence zero = doc(2, 0, "gamma delta");

        // tieA and tieB both cover 1/2 of "alpha beta": input (initial) order decides.
        RerankResult result = reranker.rerank("alpha beta", List.of(tieA, tieB, zero), null);

        assertSame(tieA, result.ranked().get(0));
        assertSame(tieB, result.ranked().get(1));
        assertSame(zero, result.ranked().get(2));
    }

    @Test
    void rerank_allCandidatesTied_preservesInputOrderExactly() {
        Evidence first = doc(1, 0, "alpha beta rich");
        Evidence second = doc(2, 0, "beta alpha");
        Evidence third = web("https://example.com/third", "alpha beta twin");

        RerankResult result = reranker.rerank("alpha beta", List.of(first, second, third), null);

        assertSame(first, result.ranked().get(0));
        assertSame(second, result.ranked().get(1));
        assertSame(third, result.ranked().get(2));
    }

    // ------------------------------------------------------------------
    // Provenance, identity, and preservation
    // ------------------------------------------------------------------

    @Test
    void rerank_preservesDocumentAndWebProvenance() {
        DocumentEvidence document = doc(42, 3, "vpn setup steps overview");
        WebEvidence webEvidence = web("https://help.example.com/vpn", "Complete VPN setup steps");
        assertEquals("handbook.pdf, chunk 3", document.provenance());
        assertEquals("Example Title, Example Publisher", webEvidence.provenance());

        RerankResult result = reranker.rerank(QUERY, List.of(document, webEvidence), null);

        List<String> provenances = result.ranked().stream().map(Evidence::provenance).toList();
        assertTrue(provenances.containsAll(
                List.of("handbook.pdf, chunk 3", "Example Title, Example Publisher")));
        assertEquals(KnowledgeSource.DOCUMENT, document.source());
        assertEquals(KnowledgeSource.WEB, webEvidence.source());
        assertEquals("https://help.example.com/vpn", webEvidence.url());
        assertEquals("web", webEvidence.retrievalMethod());
        assertEquals("hybrid", document.retrievalMethod());
    }

    @Test
    void rerank_returnsTheExactSameEvidenceInstances() {
        Evidence low = doc(1, 0, "Unrelated chatter with zero overlap");
        Evidence mid = doc(2, 0, "vpn advice");
        Evidence high = web("https://example.com/vpn", "vpn setup guide");
        List<Evidence> candidates = List.of(low, mid, high);

        RerankResult result = reranker.rerank(QUERY, candidates, null);

        assertEquals(candidates.size(), result.ranked().size());
        for (Evidence candidate : candidates) {
            assertTrue(result.ranked().stream().anyMatch(ranked -> ranked == candidate),
                    "expected the original instance to be returned: " + candidate.id());
        }
    }

    @Test
    void rerank_emptyCandidates_returnsEmptyResultWithMetadata() {
        RerankResult result = reranker.rerank(QUERY, List.of(), null);

        assertTrue(result.ranked().isEmpty());
        assertNotNull(result.metadata());
        assertEquals(LexicalReranker.RERANKER_NAME, result.metadata().rerankerName());
    }

    @Test
    void rerank_singleCandidate_returnedUnchanged() {
        Evidence only = doc(9, 1, "Something entirely unrelated");

        RerankResult result = reranker.rerank(QUERY, List.of(only), null);

        assertEquals(1, result.ranked().size());
        assertSame(only, result.ranked().get(0));
    }

    @Test
    void rerank_duplicateCandidates_areKeptAndNotDiscarded() {
        DocumentEvidence docDupA = doc(1, 0, "shared vpn content");
        DocumentEvidence docDupB = doc(1, 0, "shared vpn content");
        Evidence webDupA = web("https://same.example", "shared");
        Evidence webDupB = web("https://same.example", "shared");
        assertTrue(docDupA.equals(docDupB), "same-id documents share identity per contract");
        assertTrue(webDupA.equals(webDupB), "same-url web evidence shares identity per contract");

        RerankResult result = reranker.rerank(
                QUERY, List.of(docDupA, webDupA, docDupB, webDupB), null);

        // Reranking must not silently drop or collapse anything (dedup lives upstream).
        assertEquals(4, result.ranked().size());
        assertSame(docDupA, result.ranked().get(0));
        assertSame(docDupB, result.ranked().get(1));
        assertSame(webDupA, result.ranked().get(2));
        assertSame(webDupB, result.ranked().get(3));
    }

    // ------------------------------------------------------------------
    // Validation: invalid/null input
    // ------------------------------------------------------------------

    @Test
    void rerank_nullQuery_throwsRerankingException() {
        assertThrows(RerankingException.class, () -> reranker.rerank(null, List.of(), null));
    }

    @Test
    void rerank_blankQuery_throwsRerankingException() {
        assertThrows(RerankingException.class, () -> reranker.rerank("   ", List.of(), null));
        assertThrows(RerankingException.class, () -> reranker.rerank("\t\n", List.of(), null));
    }

    @Test
    void rerank_nullCandidates_throwsRerankingException() {
        assertThrows(RerankingException.class, () -> reranker.rerank(QUERY, null, null));
    }

    @Test
    void rerank_nullCandidateElement_throwsRerankingException() {
        List<Evidence> withNull = new ArrayList<>();
        withNull.add(doc(1, 0, "vpn setup guide"));
        withNull.add(null);

        assertThrows(RerankingException.class, () -> reranker.rerank(QUERY, withNull, null));
    }

    // ------------------------------------------------------------------
    // Preservation: no mutation of input or evidence
    // ------------------------------------------------------------------

    @Test
    void rerank_doesNotMutateTheInputList() {
        Evidence low = doc(1, 0, "Nothing relevant at all here");
        Evidence high = doc(2, 0, "vpn setup guide");
        List<Evidence> candidates = new ArrayList<>(List.of(low, high));

        reranker.rerank(QUERY, candidates, null);

        assertEquals(2, candidates.size());
        assertSame(low, candidates.get(0));
        assertSame(high, candidates.get(1));
    }

    @Test
    void rerank_doesNotMutateEvidenceObjects() {
        DocumentEvidence document = doc(5, 1, "vpn setup guide content");
        WebEvidence webEvidence = web("https://example.com/vpn", "vpn guide");

        reranker.rerank(QUERY, List.of(webEvidence, document), null);

        // Records are immutable; assert every observable field survives reranking.
        assertEquals("5::1", document.id());
        assertEquals("handbook.pdf", document.filename());
        assertEquals(1, document.chunkIndex());
        assertEquals("vpn setup guide content", document.content());
        assertEquals(0.91, document.similarity());
        assertEquals(0.0, document.keywordScore());
        assertEquals(0.85, document.hybridScore());
        assertEquals(0.85, document.retrievalScore());
        assertEquals("hybrid", document.retrievalMethod());
        assertEquals("https://example.com/vpn", webEvidence.id());
        assertEquals("https://example.com/vpn", webEvidence.url());
        assertEquals("Example Title", webEvidence.title());
        assertEquals("Example Publisher", webEvidence.publisher());
        assertEquals("vpn guide", webEvidence.content());
        assertNull(webEvidence.retrievalScore());
        assertEquals("web", webEvidence.retrievalMethod());
    }

    @Test
    void rerank_returnedRankedList_isImmutable() {
        RerankResult result = reranker.rerank(QUERY, List.of(doc(1, 0, "vpn")), null);

        assertThrows(UnsupportedOperationException.class,
                () -> result.ranked().add(doc(2, 0, "extra")));
    }

    // ------------------------------------------------------------------
    // Metadata and configuration
    // ------------------------------------------------------------------

    @Test
    void rerank_reportsDeterministicMetadata() {
        RerankResult result = reranker.rerank(QUERY, List.of(), null);

        Reranker.RerankingMetadata metadata = result.metadata();
        assertNotNull(metadata);
        assertEquals(LexicalReranker.RERANKER_NAME, metadata.rerankerName());
        assertTrue(metadata.deterministic());
        assertFalse(metadata.description().isBlank());
    }

    @Test
    void rerank_acceptsConfiguration_withoutChangingOrderOrReportedName() {
        Evidence low = doc(1, 0, "unrelated noise");
        Evidence high = doc(2, 0, "vpn setup guide");

        Reranker.RerankerConfig config =
                new Reranker.RerankerConfig("caller-supplied-label", "{\"mode\":\"strict\"}");
        RerankResult withConfig = reranker.rerank(QUERY, List.of(low, high), config);
        RerankResult withoutConfig = reranker.rerank(QUERY, List.of(low, high), null);

        assertEquals(withoutConfig, withConfig);
        assertSame(high, withConfig.ranked().get(0));
        // Metadata always reports this reranker's own name, never the caller's label.
        assertEquals(LexicalReranker.RERANKER_NAME, withConfig.metadata().rerankerName());
    }

    // ------------------------------------------------------------------
    // Signal behaviour
    // ------------------------------------------------------------------

    @Test
    void rerank_isCaseInsensitive_andIgnoresQueryStopwords() {
        Evidence noise = doc(1, 0, "What is the weather today");
        Evidence match = doc(2, 0, "VPN POLICY OVERVIEW");

        // Stopwords (what/is/the) are not terms; coverage uses {vpn, policy}.
        RerankResult result = reranker.rerank("What is the Vpn Policy", List.of(noise, match), null);

        assertSame(match, result.ranked().get(0));
        assertSame(noise, result.ranked().get(1));
    }

    @Test
    void rerank_queryWithoutLetterOrDigitTokens_preservesInitialOrder() {
        Evidence first = doc(1, 0, "first document");
        Evidence second = doc(2, 0, "second document");

        RerankResult result = reranker.rerank("???", List.of(first, second), null);

        assertSame(first, result.ranked().get(0));
        assertSame(second, result.ranked().get(1));
    }

    // ------------------------------------------------------------------
    // Contract boundary: foreign implementations via Mockito
    // ------------------------------------------------------------------

    @Test
    void rerank_foreignEvidence_ranksOnContentWithoutConsultingScores() {
        Evidence weak = mock(Evidence.class);
        when(weak.content()).thenReturn("unrelated sailing weather");
        Evidence strong = mock(Evidence.class);
        when(strong.content()).thenReturn("vpn setup guide details");

        RerankResult result = reranker.rerank(QUERY, List.of(weak, strong), null);

        assertSame(strong, result.ranked().get(0));
        assertSame(weak, result.ranked().get(1));
        // Evidence.score() is explicitly NOT comparable across providers: never read.
        verify(weak, never()).score();
        verify(strong, never()).score();
    }

    @Test
    void rerank_evidenceWithMissingContent_degradesToNoSignal() {
        Evidence noContent = mock(Evidence.class); // content() unstubbed -> null
        Evidence withContent = mock(Evidence.class);
        when(withContent.content()).thenReturn("vpn");

        RerankResult result = reranker.rerank("vpn", List.of(noContent, withContent), null);

        assertSame(withContent, result.ranked().get(0));
        assertSame(noContent, result.ranked().get(1));
    }

    // ------------------------------------------------------------------
    // No DB/provider dependencies (reflection-based, mirrors RetrievalOrchestratorTest)
    // ------------------------------------------------------------------

    @Test
    void reranker_hasNoInstanceState_noSpringAnnotation_andNoProviderDependencies() {
        for (Field field : LexicalReranker.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    "LexicalReranker must be stateless, found instance field: " + field.getName());
            String typeName = field.getType().getSimpleName();
            assertFalse(typeName.endsWith("Client") || typeName.endsWith("Repository")
                            || typeName.endsWith("Service") || typeName.endsWith("Template"),
                    "collaborator field must not exist: " + field.getName());
        }

        Constructor<?>[] constructors = LexicalReranker.class.getConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());

        for (Annotation annotation : LexicalReranker.class.getAnnotations()) {
            assertFalse(annotation.annotationType().getName().startsWith("org.springframework"),
                    "must not be a Spring bean before the integration phase: " + annotation);
        }
    }
}



