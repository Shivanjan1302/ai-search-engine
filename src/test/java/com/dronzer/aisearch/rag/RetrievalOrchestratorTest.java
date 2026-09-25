package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.dto.WebSearchResponse;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.exception.WebSearchUpstreamException;
import com.dronzer.aisearch.query.InterpretedQuery;
import com.dronzer.aisearch.query.InterpretationStatus;
import com.dronzer.aisearch.service.HybridRetrievalService;
import com.dronzer.aisearch.service.WebSearchService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for {@link RetrievalOrchestrator}.
 *
 * <p>Service-boundary mocks only: no real Tavily calls, no PostgreSQL, no
 * Testcontainers, no Spring context.</p>
 */
class RetrievalOrchestratorTest {

    private static final String EMAIL = "user@example.test";
    private static final String QUESTION = "What does the leave policy say?";

    private final HybridRetrievalService documentRetrieval = mock(HybridRetrievalService.class);
    private final WebSearchService webRetrieval = mock(WebSearchService.class);
    private final RetrievalOrchestrator orchestrator =
            new RetrievalOrchestrator(documentRetrieval, webRetrieval);

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private static SourcePlan plan(
            List<SourceRequirement> sources,
            boolean mixedSource,
            boolean requiresFreshness,
            FallbackPolicy fallbackPolicy) {
        return new SourcePlan(sources, mixedSource, requiresFreshness,
                EvidenceRequirement.MODERATE, fallbackPolicy);
    }

    private static InterpretedQuery query() {
        return new InterpretedQuery(QUESTION);
    }

    private static SemanticSearchResult docResult(long documentId, int chunkIndex, String text) {
        return new SemanticSearchResult(documentId, "handbook.pdf", chunkIndex, text,
                0.91, 0.4, 0.8);
    }

    private static WebSearchResult webResult(String url) {
        return new WebSearchResult("Title for " + url, url, "Snippet for " + url, "Acme HR");
    }

    private void stubDocuments(List<SemanticSearchResult> results) {
        when(documentRetrieval.retrieve(eq(QUESTION),
                eq(RetrievalOrchestrator.DEFAULT_DOCUMENT_CANDIDATE_LIMIT), eq(EMAIL)))
                .thenReturn(results);
    }

    private void stubWeb(List<WebSearchResult> results) {
        when(webRetrieval.search(QUESTION, RetrievalOrchestrator.DEFAULT_WEB_RESULT_LIMIT))
                .thenReturn(new WebSearchResponse(QUESTION, results));
    }

    private static List<String> evidenceIds(RetrievalResult result) {
        return result.evidence().stream().map(Evidence::id).collect(Collectors.toList());
    }

    private static List<KnowledgeSource> evidenceSources(RetrievalResult result) {
        return result.evidence().stream().map(Evidence::source).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 1–6: Source selection per SourcePlan (incl. optional-execution rule).
    // ------------------------------------------------------------------

    @Test
    void documentRequired_executesDocumentRetrieval() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                false, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "Leave is 30 days")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        verify(documentRetrieval).retrieve(QUESTION,
                RetrievalOrchestrator.DEFAULT_DOCUMENT_CANDIDATE_LIMIT, EMAIL);
        assertEquals(List.of(KnowledgeSource.DOCUMENT), result.executedSources());
        assertEquals(List.of(KnowledgeSource.DOCUMENT), evidenceSources(result));
        verifyNoInteractions(webRetrieval);
    }

    @Test
    void webRequired_executesWebRetrieval() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                false, false, FallbackPolicy.FAIL_FAST);
        stubWeb(List.of(webResult("https://example.com/news")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        verify(webRetrieval).search(QUESTION, RetrievalOrchestrator.DEFAULT_WEB_RESULT_LIMIT);
        assertEquals(List.of(KnowledgeSource.WEB), result.executedSources());
        assertEquals(List.of(KnowledgeSource.WEB), evidenceSources(result));
        verifyNoInteractions(documentRetrieval);
    }

    @Test
    void bothRequired_executesBoth_withDocumentCategoryFirstInCombinedEvidence() {
        // Plan order lists WEB first; category ordering (rule 2) must still put
        // document evidence first in the combined view because both are required.
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                true, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));
        stubWeb(List.of(webResult("https://example.com/a")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(List.of(KnowledgeSource.WEB, KnowledgeSource.DOCUMENT),
                result.executedSources());
        assertEquals(List.of("1::0", "https://example.com/a"), evidenceIds(result));
        assertEquals(1, result.documents().size());
        assertEquals(1, result.webEvidence().size());
        assertTrue(result.unavailableSources().isEmpty());
    }

    @Test
    void documentRequiredWebOptional_doesNotExecuteWebSearch() {
        // DefaultSourcePlanner DOCUMENT plan: mixed=false, fresh=false.
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB,
                        "Web is supplementary for document-specific queries"),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                false, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        verifyNoInteractions(webRetrieval);
        assertEquals(List.of(KnowledgeSource.DOCUMENT), result.executedSources());
        assertTrue(result.webEvidence().isEmpty());
    }

    @Test
    void webRequiredDocumentOptional_doesNotExecuteDocumentRetrieval() {
        // DefaultSourcePlanner CURRENT plan: WEB required, DOCUMENT optional,
        // mixed=false, fresh=true. Freshness justifies optional WEB only — never
        // optional document retrieval.
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.DOCUMENT,
                        "Documents are supplementary for current-information queries"),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                false, true, FallbackPolicy.DEGRADE_GRADUALLY);
        stubWeb(List.of(webResult("https://example.com/live")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        verifyNoInteractions(documentRetrieval);
        assertEquals(List.of(KnowledgeSource.WEB), result.executedSources());
        assertTrue(result.documents().isEmpty());
    }

    @Test
    void allOptionalGeneralPlan_withoutPlanFlags_executesNothing() {
        // DefaultSourcePlanner GENERAL plan: everything optional, mixed=false,
        // fresh=false → OPTIONAL does not mean "always execute".
        SourcePlan plan = plan(List.of(
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                false, false, FallbackPolicy.FAIL_FAST);

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        verifyNoInteractions(documentRetrieval, webRetrieval);
        assertFalse(result.hasEvidence());
        assertTrue(result.executedSources().isEmpty());
        assertTrue(result.unavailableSources().isEmpty());
    }

    @Test
    void allOptionalPlan_withFreshness_executesOnlyWeb() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                false, true, FallbackPolicy.DEGRADE_GRADUALLY);
        stubWeb(List.of(webResult("https://example.com/today")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        verifyNoInteractions(documentRetrieval);
        assertEquals(List.of(KnowledgeSource.WEB), result.executedSources());
        assertEquals(List.of(KnowledgeSource.WEB), evidenceSources(result));
    }

    @Test
    void allOptionalPlan_withMixedSource_executesBothRetrievableSources() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                true, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));
        stubWeb(List.of(webResult("https://example.com/a")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(List.of(KnowledgeSource.DOCUMENT, KnowledgeSource.WEB),
                result.executedSources());
        assertEquals(1, result.documents().size());
        assertEquals(1, result.webEvidence().size());
    }

    // ------------------------------------------------------------------
    // 7–8: Evidence conversion goes through the existing adapters.
    // ------------------------------------------------------------------

    @Test
    void documentResults_convertedThroughDocumentRetrievalAdapter() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);
        SemanticSearchResult raw = new SemanticSearchResult(
                7L, "handbook.pdf", 2, "Probation lasts 90 days", 0.91, 0.4, 0.8);
        stubDocuments(List.of(raw));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(1, result.documents().size());
        DocumentEvidence evidence = result.documents().get(0);
        // Byte-for-byte identical to what DocumentRetrievalAdapter produces: no
        // duplicated or divergent mapping logic in the orchestrator.
        assertEquals(DocumentRetrievalAdapter.fromResult(raw), evidence);
        assertEquals(7L, evidence.documentId());
        assertEquals("handbook.pdf", evidence.filename());
        assertEquals(2, evidence.chunkIndex());
        assertEquals("Probation lasts 90 days", evidence.content());
        assertEquals(0.91, evidence.similarity());
        assertEquals(0.4, evidence.keywordScore());
        assertEquals(0.8, evidence.hybridScore());
        assertEquals("semantic", evidence.retrievalMethod());
        assertEquals("7::2", evidence.id());
        assertEquals("handbook.pdf, chunk 2", evidence.provenance());
    }

    @Test
    void webResults_convertedThroughWebRetrievalAdapter() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.WEB)),
                false, false, FallbackPolicy.FAIL_FAST);
        WebSearchResult raw = new WebSearchResult(
                "Leave Policy Explained", "https://example.com/leave",
                "Everything about leave", "Acme HR");
        stubWeb(List.of(raw));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(1, result.webEvidence().size());
        WebEvidence evidence = result.webEvidence().get(0);
        assertEquals(WebRetrievalAdapter.fromResult(raw), evidence);
        assertEquals("https://example.com/leave", evidence.url());
        assertEquals("Leave Policy Explained", evidence.title());
        assertEquals("Acme HR", evidence.publisher());
        assertEquals("Everything about leave", evidence.content());
        assertEquals("web", evidence.retrievalMethod());
        assertEquals("https://example.com/leave", evidence.id());
    }

    // ------------------------------------------------------------------
    // 9–10: Exact-identity deduplication.
    // ------------------------------------------------------------------

    @Test
    void duplicateDocumentEvidence_removedFirstOccurrenceWins() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);
        SemanticSearchResult rankedFirst = new SemanticSearchResult(
                1L, "handbook.pdf", 0, "higher ranked", 0.95);
        SemanticSearchResult sameIdentity = new SemanticSearchResult(
                1L, "handbook.pdf", 0, "lower ranked duplicate", 0.55);
        SemanticSearchResult other = new SemanticSearchResult(
                2L, "handbook.pdf", 0, "other chunk", 0.7);
        stubDocuments(List.of(rankedFirst, sameIdentity, other));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(2, result.documents().size());
        assertEquals("higher ranked", result.documents().get(0).content());
        assertEquals(List.of("1::0", "2::0"),
                result.documents().stream().map(DocumentEvidence::id).toList());
    }

    @Test
    void duplicateWebEvidence_removedFirstOccurrenceWins() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.WEB)),
                false, false, FallbackPolicy.FAIL_FAST);
        WebSearchResult rankedFirst = new WebSearchResult(
                "Original title", "https://example.com/page", "first snippet", "Acme");
        WebSearchResult sameUrl = new WebSearchResult(
                "Different title", "https://example.com/page", "second snippet", "Other");
        WebSearchResult other = new WebSearchResult(
                "Other page", "https://example.com/other", "third snippet", "Acme");
        stubWeb(List.of(rankedFirst, sameUrl, other));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(2, result.webEvidence().size());
        assertEquals("Original title", result.webEvidence().get(0).title());
        assertEquals(List.of("https://example.com/page", "https://example.com/other"),
                result.webEvidence().stream().map(WebEvidence::id).toList());
    }

    // ------------------------------------------------------------------
    // 11: Deterministic ordering of the combined view.
    // ------------------------------------------------------------------

    @Test
    void combinedEvidence_requiredTierFirstAndWithinSourceOrderPreserved_deterministicAcrossRuns() {
        // WEB required listed first, DOCUMENT optional second (mixed=true so the
        // optional source qualifies). Expected combined order: required-tier web
        // evidence in provider order, then optional-tier document evidence in
        // retrieval order — identical across repeated invocations.
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.DOCUMENT)),
                true, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(
                docResult(1L, 0, "first doc"),
                docResult(2L, 0, "second doc")));
        stubWeb(List.of(
                webResult("https://example.com/1"),
                webResult("https://example.com/2")));

        RetrievalResult first = orchestrator.retrieve(plan, query(), EMAIL);
        RetrievalResult second = orchestrator.retrieve(plan, query(), EMAIL);

        List<String> expectedIds = List.of(
                "https://example.com/1",
                "https://example.com/2",
                "1::0",
                "2::0");
        assertEquals(expectedIds, evidenceIds(first));
        assertEquals(expectedIds, evidenceIds(second));
        assertEquals(evidenceIds(first), evidenceIds(second));
        assertEquals(List.of(KnowledgeSource.WEB, KnowledgeSource.DOCUMENT),
                first.executedSources());
    }

    // ------------------------------------------------------------------
    // 12–13: Required vs optional failure semantics.
    // ------------------------------------------------------------------

    @Test
    void requiredSourceFailure_failFast_throwsRetrievalExceptionWithCause() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);
        RuntimeException upstream = new RuntimeException("connection pool exhausted");
        when(documentRetrieval.retrieve(anyString(), anyInt(), anyString())).thenThrow(upstream);

        RetrievalException thrown = assertThrows(RetrievalException.class,
                () -> orchestrator.retrieve(plan, query(), EMAIL));

        assertTrue(thrown.getMessage().contains("DOCUMENT"));
        assertSame(upstream, thrown.getCause());
        verifyNoInteractions(webRetrieval);
    }

    @Test
    void requiredSourceFailure_degradeGradually_recordsAndContinues() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.required(KnowledgeSource.WEB)),
                true, false, FallbackPolicy.DEGRADE_GRADUALLY);
        when(documentRetrieval.retrieve(anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("vector store down"));
        stubWeb(List.of(webResult("https://example.com/backup")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertTrue(result.documents().isEmpty());
        assertEquals(1, result.webEvidence().size());
        assertEquals(List.of(KnowledgeSource.DOCUMENT, KnowledgeSource.WEB),
                result.executedSources());
        assertEquals(List.of(KnowledgeSource.DOCUMENT), result.unavailableSources());
        assertEquals(List.of(KnowledgeSource.WEB), evidenceSources(result));
    }

    @Test
    void optionalSourceFailure_preservesRequiredEvidence() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB)),
                false, true, FallbackPolicy.FAIL_FAST); // fresh=true ⇒ optional web runs
        stubDocuments(List.of(docResult(1L, 0, "private document chunk")));
        when(webRetrieval.search(anyString(), anyInt()))
                .thenThrow(new WebSearchUpstreamException());

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(1, result.documents().size());
        assertEquals("private document chunk", result.documents().get(0).content());
        assertTrue(result.webEvidence().isEmpty());
        assertEquals(List.of(KnowledgeSource.WEB), result.unavailableSources());
        assertEquals(List.of(KnowledgeSource.DOCUMENT), evidenceSources(result));
    }

    // ------------------------------------------------------------------
    // 14: Tenant identity reaches the existing retrieval layer unchanged.
    // ------------------------------------------------------------------

    @Test
    void tenantEmail_reachesExistingDocumentRetrievalUnchanged() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));

        orchestrator.retrieve(plan, query(), EMAIL);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentRetrieval).retrieve(
                eq(QUESTION),
                eq(RetrievalOrchestrator.DEFAULT_DOCUMENT_CANDIDATE_LIMIT),
                emailCaptor.capture());
        assertEquals(EMAIL, emailCaptor.getValue());
        // The orchestrator does no ownership filtering of its own — the identity is
        // simply forwarded to the tenant-aware HybridRetrievalService.
    }

    // ------------------------------------------------------------------
    // 15: No direct provider/database dependencies in orchestrator or adapters.
    // ------------------------------------------------------------------

    @Test
    void orchestrator_andAdapters_haveNoProviderOrDatabaseDependencies() {
        Constructor<?>[] constructors = RetrievalOrchestrator.class.getConstructors();
        assertEquals(2, constructors.length);
        assertTrue(constructors[0].getParameterCount() == 2
                || constructors[1].getParameterCount() == 2);
        assertTrue(constructors[0].getParameterCount() == 3
                || constructors[1].getParameterCount() == 3);
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2) {
                assertArrayEquals(
                        new Class<?>[]{HybridRetrievalService.class, WebSearchService.class},
                        parameters);
            } else {
                assertArrayEquals(
                        new Class<?>[]{HybridRetrievalService.class, WebSearchService.class,
                                com.dronzer.aisearch.query.DocumentContextResolver.class},
                        parameters);
            }
        }

        for (Field field : RetrievalOrchestrator.class.getDeclaredFields()) {
            String typeName = field.getType().getSimpleName();
            assertFalse(typeName.endsWith("Client"),
                    "orchestrator must not depend on a provider client: " + typeName);
            assertFalse(typeName.contains("Repository"),
                    "orchestrator must not depend on a repository: " + typeName);
            assertFalse(typeName.contains("EntityManager"),
                    "orchestrator must not depend on the database: " + typeName);
        }

        // Adapters are pure static translators: zero state, zero injected
        // provider/database references.
        assertEquals(0, DocumentRetrievalAdapter.class.getDeclaredFields().length);
        assertEquals(0, WebRetrievalAdapter.class.getDeclaredFields().length);
    }

    // ------------------------------------------------------------------
    // Additional documented decisions.
    // ------------------------------------------------------------------

    @Test
    void modelKnowledgeSource_isNeverExecuted() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.required(KnowledgeSource.MODEL_KNOWLEDGE),
                SourceRequirement.optional(KnowledgeSource.DOCUMENT)),
                true, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));

        RetrievalResult result = orchestrator.retrieve(plan, query(), EMAIL);

        assertEquals(List.of(KnowledgeSource.DOCUMENT), result.executedSources());
        assertFalse(result.executedSources().contains(KnowledgeSource.MODEL_KNOWLEDGE));
        verifyNoInteractions(webRetrieval);
    }

    @Test
    void retrievalQuery_usesNormalizedQueryWhenPresent() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));
        InterpretedQuery rewritten = new InterpretedQuery(QUESTION)
                .withNormalizedQuery("rewritten standalone form");

        orchestrator.retrieve(plan, rewritten, EMAIL);

        verify(documentRetrieval).retrieve(eq("rewritten standalone form"),
                eq(RetrievalOrchestrator.DEFAULT_DOCUMENT_CANDIDATE_LIMIT), eq(EMAIL));
        verify(documentRetrieval, never()).retrieve(eq(QUESTION),
                anyInt(), anyString());
    }

    @Test
    void retrievalQuery_ignoresNormalizedValueWhenInterpretationIsAmbiguous() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);
        stubDocuments(List.of(docResult(1L, 0, "doc one")));
        InterpretedQuery ambiguous = new InterpretedQuery(QUESTION)
                .withNormalizedQuery("untrusted normalized form")
                .withInterpretationStatus(InterpretationStatus.AMBIGUOUS);

        orchestrator.retrieve(plan, ambiguous, EMAIL);

        verify(documentRetrieval).retrieve(eq(QUESTION),
                eq(RetrievalOrchestrator.DEFAULT_DOCUMENT_CANDIDATE_LIMIT), eq(EMAIL));
        verify(documentRetrieval, never()).retrieve(eq("untrusted normalized form"),
                anyInt(), anyString());
    }

    @Test
    void blankInputs_areRejected() {
        SourcePlan plan = plan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, FallbackPolicy.FAIL_FAST);

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.retrieve(plan, new InterpretedQuery("  "), EMAIL));
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.retrieve(plan, query(), " "));
        assertThrows(NullPointerException.class,
                () -> orchestrator.retrieve(null, query(), EMAIL));
        assertThrows(NullPointerException.class,
                () -> orchestrator.retrieve(plan, null, EMAIL));
    }
}
