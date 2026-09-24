package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.dto.WebSearchResponse;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.service.HybridRetrievalService;
import com.dronzer.aisearch.service.WebSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** End-to-end orchestration tests with all external boundaries mocked. */
class ProductionRagPipelineTest {

    private static final String EMAIL = "tenant@example.test";

    private HybridRetrievalService documents;
    private WebSearchService web;
    private GenerationModel model;

    @BeforeEach
    void setUp() {
        documents = mock(HybridRetrievalService.class);
        web = mock(WebSearchService.class);
        model = prompt -> "Supported answer";
    }

    @Test
    void documentSpecificQueryUsesDocumentsAndPreservesTenantAndSourceFields() {
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL)))
                .thenReturn(List.of(new SemanticSearchResult(
                        7L, "policy.pdf", 2, "Leave policy content", 0.91, 0.2, 0.71)));

        var result = realPipeline().execute("What does my document say about leave?", EMAIL);

        assertThat(result.response().origin()).isEqualTo(RagOrigin.DOCUMENTS);
        assertThat(result.response().sources()).containsExactly(
                new com.dronzer.aisearch.dto.RagSource(
                        7L, "policy.pdf", 2, 0.91, 0.2, 0.71));
        assertThat(result.response().webSources()).isEmpty();
        assertThat(result.provenance().sourcesContributing())
                .containsExactly(KnowledgeSource.DOCUMENT);
        verify(documents).retrieve(anyString(), eq(20), eq(EMAIL));
        verify(web, never()).search(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }


    @Test
    void currentQueryRetrievesWebAndPreservesProviderMetadata() {
        WebSearchResult result = new WebSearchResult(
                "Current title", "https://current.test/news", "Current fact", "publisher",
                "current.test", "/news", "current.test › news", "2026-09-24");
        when(web.search(anyString(), eq(5)))
                .thenReturn(new WebSearchResponse("query", List.of(result)));

        var response = realPipeline().execute("What is the latest news today?", EMAIL).response();

        assertThat(response.origin()).isEqualTo(RagOrigin.WEB);
        assertThat(response.sources()).isEmpty();
        assertThat(response.webSources()).containsExactly(result);
        verify(web).search(anyString(), eq(5));
    }

    @Test
    void mixedQueryKeepsDocumentAndWebEvidenceDistinct() {
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL)))
                .thenReturn(List.of(new SemanticSearchResult(
                        1L, "internal.pdf", 0, "Internal current plan", 0.8)));
        when(web.search(anyString(), eq(5))).thenReturn(new WebSearchResponse(
                "query", List.of(new WebSearchResult(
                        "External", "https://external.test", "External current fact", "site"))));

        var response = realPipeline()
                .execute("According to my document, what is the latest update?", EMAIL)
                .response();

        assertThat(response.origin()).isEqualTo(RagOrigin.MIXED);
        assertThat(response.sources()).hasSize(1);
        assertThat(response.webSources()).hasSize(1);
    }

    @Test
    void generalKnowledgeUsesModelKnowledgeWithoutRetrievingEitherSource() {
        var result = realPipeline().execute("What is retrieval augmented generation?", EMAIL);

        assertThat(result.response().origin()).isEqualTo(RagOrigin.MODEL_KNOWLEDGE);
        assertThat(result.response().sources()).isEmpty();
        assertThat(result.response().webSources()).isEmpty();
        assertThat(result.provenance().evidenceCounts().orElseThrow().modelKnowledgeCount())
                .isEqualTo(1);
        verify(documents, never()).retrieve(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());
        verify(web, never()).search(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void documentQueryWithoutEvidenceRefusesWithoutModelSubstitution() {
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL))).thenReturn(List.of());
        model = prompt -> {
            throw new AssertionError("model must not be invoked for an insufficient policy");
        };

        var result = realPipeline().execute("Summarize my document", EMAIL);

        assertThat(result.response().answer())
                .isEqualTo(ProductionRagPipeline.INSUFFICIENT_EVIDENCE_ANSWER);
        assertThat(result.response().origin()).isEqualTo(RagOrigin.INSUFFICIENT_EVIDENCE);
        assertThat(result.provenance().generationRefused()).isTrue();
    }

    @Test
    void requiredDocumentFailurePropagatesRetrievalExceptionAndCause() {
        RuntimeException cause = new IllegalStateException("vector store unavailable");
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL))).thenThrow(cause);

        assertThatThrownBy(() -> realPipeline().execute("Summarize my document", EMAIL))
                .isInstanceOf(RetrievalException.class)
                .hasCause(cause);
    }

    @Test
    void optionalSourceFailureIsRecordedAndDocumentAnswerSurvives() {
        model = prompt -> "The optional web source was unavailable. [E1]";
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL)))
                .thenReturn(List.of(new SemanticSearchResult(
                        3L, "notes.txt", 0, "General background from private notes", 0.8)));
        when(web.search(anyString(), eq(5)))
                .thenThrow(new IllegalStateException("web unavailable"));

        SourcePlanner planner = mock(SourcePlanner.class);
        SourcePlan plan = new SourcePlan(
                List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT),
                        SourceRequirement.optional(KnowledgeSource.WEB),
                        SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)),
                true, false, EvidenceRequirement.MODERATE, FallbackPolicy.FAIL_FAST);
        when(planner.plan(any())).thenReturn(plan);
        ProductionRagPipeline pipeline = pipelineWithPlanner(planner);

        var result = pipeline.execute("Explain the private note", EMAIL);

        assertThat(result.response().origin()).isEqualTo(RagOrigin.DOCUMENTS);
        assertThat(result.provenance().failures()).extracting(
                ProvenanceAssembler.PipelineFailure::source)
                .contains(Optional.of("WEB"));
    }

    @Test
    void everyStageIsInvokedExactlyOnceWithTheSameEvidence() {
        SourcePlanner planner = mock(SourcePlanner.class);
        RetrievalOrchestrator retrieval = mock(RetrievalOrchestrator.class);
        Reranker reranker = mock(Reranker.class);
        ContextBuilder builder = mock(ContextBuilder.class);
        EvidencePolicy policy = mock(EvidencePolicy.class);
        GroundedGenerator generator = mock(GroundedGenerator.class);
        CitationValidator citations = mock(CitationValidator.class);
        ProvenanceAssembler provenance = mock(ProvenanceAssembler.class);
        DocumentEvidence evidence = documentEvidence();
        SourcePlan plan = new SourcePlan(
                List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT)),
                false, false, EvidenceRequirement.STRICT, FallbackPolicy.FAIL_FAST);
        RetrievalResult retrieved = new RetrievalResult(
                List.of(evidence), List.of(), List.of(evidence),
                List.of(KnowledgeSource.DOCUMENT), List.of());
        RerankResult reranked = new RerankResult(
                List.of(evidence), new Reranker.RerankingMetadata("test", true, null));
        ContextBuilder.ConstructionResult constructed = new ContextBuilder.ConstructionResult(
                List.of(new ContextPiece(evidence)), List.of(), null);
        EvidencePolicyDecision decision = new EvidencePolicyDecision(
                true, "sufficient", List.of(KnowledgeSource.DOCUMENT),
                false, false, Optional.of("covered"), false);
        GenerationResult generated = new GenerationResult(
                "Answer [E1]", false, Optional.empty(), Optional.empty(),
                List.of(new ContextPiece(evidence)), Optional.of(decision), Optional.empty());
        ValidationResult validated = new ValidationResult(
                true, false, "valid", Optional.of(List.of()));

        when(planner.plan(any())).thenReturn(plan);
        when(retrieval.retrieve(eq(plan), any(), eq(EMAIL))).thenReturn(retrieved);
        when(reranker.rerank(anyString(), eq(retrieved.evidence()), eq(null))).thenReturn(reranked);
        when(builder.build(anyString(), eq(retrieved.evidence()), eq(Optional.of(reranked)), any()))
                .thenReturn(constructed);
        when(policy.evaluate(anyString(), eq(List.of(evidence)), eq(plan), eq(List.of())))
                .thenReturn(decision);
        when(generator.generate(any())).thenReturn(generated);
        when(citations.validate(anyString(), eq("Answer [E1]"), eq(generated.contextPieces()), any()))
                .thenReturn(validated);
        when(provenance.assemble(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(ProvenanceAssembler.Provenance.class));

        ProductionRagPipeline pipeline = new ProductionRagPipeline(
                planner, retrieval, reranker, builder, policy, generator, citations, provenance);
        pipeline.execute("What does my document say?", EMAIL);

        verify(retrieval, times(1)).retrieve(eq(plan), any(), eq(EMAIL));
        verify(reranker, times(1)).rerank(anyString(), eq(retrieved.evidence()), eq(null));
        verify(builder, times(1)).build(anyString(), eq(retrieved.evidence()), any(), any());
        verify(policy, times(1)).evaluate(anyString(), eq(List.of(evidence)), eq(plan), eq(List.of()));
        verify(generator, times(1)).generate(any());
        verify(citations, times(1)).validate(anyString(), eq("Answer [E1]"), any(), any());
        verify(provenance, times(1)).assemble(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidCitationProducesDeterministicRefusalAndFailedProvenance() {
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL)))
                .thenReturn(List.of(new SemanticSearchResult(
                        9L, "policy.pdf", 0, "Policy content", 0.9)));
        model = prompt -> "Invented answer [E9]";

        var result = realPipeline().execute("What does my document say?", EMAIL);

        assertThat(result.response().answer())
                .isEqualTo(ProductionRagPipeline.INSUFFICIENT_EVIDENCE_ANSWER);
        assertThat(result.response().origin()).isEqualTo(RagOrigin.INSUFFICIENT_EVIDENCE);
        assertThat(result.response().sources()).isEmpty();
        assertThat(result.provenance().validationPerformed()).isTrue();
        assertThat(result.provenance().validationPassed()).isFalse();
        assertThat(result.provenance().failures()).extracting(
                ProvenanceAssembler.PipelineFailure::kind)
                .contains(ProvenanceAssembler.FailureKind.VALIDATION_FAILURE);
    }

    @Test
    void identicalInputsProduceIdenticalPipelineResultTwice() {
        when(documents.retrieve(anyString(), eq(20), eq(EMAIL)))
                .thenReturn(List.of(new SemanticSearchResult(
                        4L, "same.pdf", 0, "Stable evidence", 0.8)));
        ProductionRagPipeline pipeline = realPipeline();

        var first = pipeline.execute("What does my document say?", EMAIL);
        var second = pipeline.execute("What does my document say?", EMAIL);

        assertThat(first.response()).isEqualTo(second.response());
        assertThat(first.provenance().sourcesContributing())
                .isEqualTo(second.provenance().sourcesContributing());
        verify(documents, times(2)).retrieve(anyString(), eq(20), eq(EMAIL));
    }

    @Test
    void tenantEmailReachesRetrievalBoundaryUnchanged() {
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        when(documents.retrieve(anyString(), eq(20), email.capture()))
                .thenReturn(List.of(new SemanticSearchResult(
                        1L, "tenant.pdf", 0, "Tenant content", 0.8)));

        realPipeline().execute("What does my document say?", "Exact.Case@Example.Test");

        assertThat(email.getValue()).isEqualTo("Exact.Case@Example.Test");
    }

    private ProductionRagPipeline realPipeline() {
        return pipelineWithPlanner(DefaultSourcePlanner.create());
    }

    private ProductionRagPipeline pipelineWithPlanner(SourcePlanner planner) {
        RetrievalOrchestrator retrieval = new RetrievalOrchestrator(documents, web);
        return new ProductionRagPipeline(
                planner,
                retrieval,
                new LexicalReranker(),
                new DefaultContextBuilder(),
                DefaultEvidencePolicy.create(),
                new DefaultGroundedGenerator(model),
                new DefaultCitationValidator(),
                new DefaultProvenanceAssembler());
    }

    private static DocumentEvidence documentEvidence() {
        return new DocumentEvidence(
                8L, "manual.pdf", 1, "Manual content", 0.8, 0.1, 0.6, 0.8, "hybrid");
    }
}


