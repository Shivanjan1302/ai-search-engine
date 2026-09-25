package com.dronzer.aisearch.config;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.query.DefaultQueryInterpreter;
import com.dronzer.aisearch.query.DocumentContextResolver;
import com.dronzer.aisearch.query.QueryInterpretationService;
import com.dronzer.aisearch.rag.AIClientGenerationModel;
import com.dronzer.aisearch.rag.CitationValidator;
import com.dronzer.aisearch.rag.ContextBuilder;
import com.dronzer.aisearch.rag.DefaultCitationValidator;
import com.dronzer.aisearch.rag.DefaultContextBuilder;
import com.dronzer.aisearch.rag.DefaultEvidencePolicy;
import com.dronzer.aisearch.rag.DefaultGroundedGenerator;
import com.dronzer.aisearch.rag.DefaultProvenanceAssembler;
import com.dronzer.aisearch.rag.DefaultSourcePlanner;
import com.dronzer.aisearch.rag.EvidencePolicy;
import com.dronzer.aisearch.rag.GenerationModel;
import com.dronzer.aisearch.rag.GroundedGenerator;
import com.dronzer.aisearch.rag.LexicalReranker;
import com.dronzer.aisearch.rag.ProvenanceAssembler;
import com.dronzer.aisearch.rag.ProductionRagPipeline;
import com.dronzer.aisearch.rag.Reranker;
import com.dronzer.aisearch.rag.RetrievalOrchestrator;
import com.dronzer.aisearch.rag.SourcePlanner;
import com.dronzer.aisearch.service.HybridRetrievalService;
import com.dronzer.aisearch.service.WebSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Production wiring for the existing Phase 2B RAG stage contracts. */
@Configuration
public class RagConfig {

    @Bean
    SourcePlanner sourcePlanner() {
        return DefaultSourcePlanner.create();
    }

    @Bean
    QueryInterpretationService queryInterpretationService() {
        return new DefaultQueryInterpreter();
    }

    @Bean
    RetrievalOrchestrator retrievalOrchestrator(
            HybridRetrievalService documentRetrieval,
            WebSearchService webRetrieval,
            DocumentContextResolver documentContextResolver) {
        return new RetrievalOrchestrator(
                documentRetrieval, webRetrieval, documentContextResolver);
    }

    @Bean
    Reranker reranker() {
        return new LexicalReranker();
    }

    @Bean
    ContextBuilder contextBuilder() {
        return new DefaultContextBuilder();
    }

    @Bean
    EvidencePolicy evidencePolicy() {
        return DefaultEvidencePolicy.create();
    }

    @Bean
    GenerationModel generationModel(AIClient aiClient) {
        return new AIClientGenerationModel(aiClient);
    }

    @Bean
    GroundedGenerator groundedGenerator(GenerationModel generationModel) {
        return new DefaultGroundedGenerator(generationModel);
    }

    @Bean
    CitationValidator citationValidator() {
        return new DefaultCitationValidator();
    }

    @Bean
    ProvenanceAssembler provenanceAssembler() {
        return new DefaultProvenanceAssembler();
    }

    @Bean
    ProductionRagPipeline productionRagPipeline(
            QueryInterpretationService queryInterpretationService,
            SourcePlanner sourcePlanner,
            RetrievalOrchestrator retrievalOrchestrator,
            Reranker reranker,
            ContextBuilder contextBuilder,
            EvidencePolicy evidencePolicy,
            GroundedGenerator groundedGenerator,
            CitationValidator citationValidator,
            ProvenanceAssembler provenanceAssembler) {
        return new ProductionRagPipeline(
                queryInterpretationService,
                sourcePlanner, retrievalOrchestrator, reranker, contextBuilder,
                evidencePolicy, groundedGenerator, citationValidator, provenanceAssembler);
    }
}
