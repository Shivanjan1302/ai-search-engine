package com.dronzer.aisearch.evaluation;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.rag.AIClientGenerationModel;
import com.dronzer.aisearch.rag.DefaultCitationValidator;
import com.dronzer.aisearch.rag.DefaultContextBuilder;
import com.dronzer.aisearch.rag.DefaultEvidencePolicy;
import com.dronzer.aisearch.rag.DefaultGroundedGenerator;
import com.dronzer.aisearch.rag.DefaultProvenanceAssembler;
import com.dronzer.aisearch.rag.DefaultSourcePlanner;
import com.dronzer.aisearch.rag.LexicalReranker;
import com.dronzer.aisearch.rag.ProductionRagPipeline;
import com.dronzer.aisearch.rag.RetrievalOrchestrator;
import com.dronzer.aisearch.service.HybridRetrievalService;
import com.dronzer.aisearch.service.WebSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Deterministic evaluation of the Phase 2B runtime boundaries. */
class RagEvaluationTest {

    private HybridRetrievalService documents;
    private WebSearchService web;
    private AIClient model;
    private ProductionRagPipeline pipeline;

    @BeforeEach
    void setUp() {
        documents = mock(HybridRetrievalService.class);
        web = mock(WebSearchService.class);
        model = mock(AIClient.class);
        pipeline = new ProductionRagPipeline(
                DefaultSourcePlanner.create(),
                new RetrievalOrchestrator(documents, web),
                new LexicalReranker(),
                new DefaultContextBuilder(),
                DefaultEvidencePolicy.create(),
                new DefaultGroundedGenerator(new AIClientGenerationModel(model)),
                new DefaultCitationValidator(),
                new DefaultProvenanceAssembler());
    }

    @Test
    void documentSpecificEvaluationIsGroundedAndTenantScoped() {
        when(documents.retrieve(anyString(), anyInt(), anyString())).thenReturn(List.of(
                new com.dronzer.aisearch.dto.SemanticSearchResult(
                        1L, "guide.pdf", 0, "RAG retrieves context before generation.", 0.9)));
        when(model.generateAnswer(anyString())).thenReturn("RAG retrieves context [E1].");

        var response = pipeline.execute("What does my document say about RAG?", "a@example.test")
                .response();

        assertThat(response.origin()).isEqualTo(RagOrigin.DOCUMENTS);
        assertThat(response.answer()).contains("RAG retrieves context");
        org.mockito.Mockito.verify(documents).retrieve(anyString(), anyInt(),
                org.mockito.ArgumentMatchers.eq("a@example.test"));
    }

    @Test
    void generalKnowledgeEvaluationUsesModelWithoutEvidence() {
        when(model.generateAnswer(anyString())).thenReturn("RAG is a grounded generation technique.");

        var response = pipeline.execute("What is RAG?", "a@example.test").response();

        assertThat(response.origin()).isEqualTo(RagOrigin.MODEL_KNOWLEDGE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.webSources()).isEmpty();
    }
}
