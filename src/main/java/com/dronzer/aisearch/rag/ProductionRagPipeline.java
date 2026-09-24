package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.RagSource;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.query.InterpretedQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The single authoritative application-level orchestration path for grounded RAG.
 * Each dependency is a stage boundary; this class only coordinates stage inputs
 * and maps the final immutable result into the existing public response contract.
 */
public final class ProductionRagPipeline {

    static final String INSUFFICIENT_EVIDENCE_ANSWER =
            "I could not find relevant information in your documents.";

    private final SourcePlanner sourcePlanner;
    private final RetrievalOrchestrator retrievalOrchestrator;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;
    private final EvidencePolicy evidencePolicy;
    private final GroundedGenerator groundedGenerator;
    private final CitationValidator citationValidator;
    private final ProvenanceAssembler provenanceAssembler;

    public ProductionRagPipeline(
            SourcePlanner sourcePlanner,
            RetrievalOrchestrator retrievalOrchestrator,
            Reranker reranker,
            ContextBuilder contextBuilder,
            EvidencePolicy evidencePolicy,
            GroundedGenerator groundedGenerator,
            CitationValidator citationValidator,
            ProvenanceAssembler provenanceAssembler) {
        this.sourcePlanner = Objects.requireNonNull(sourcePlanner, "sourcePlanner must not be null");
        this.retrievalOrchestrator = Objects.requireNonNull(
                retrievalOrchestrator, "retrievalOrchestrator must not be null");
        this.reranker = Objects.requireNonNull(reranker, "reranker must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.evidencePolicy = Objects.requireNonNull(evidencePolicy, "evidencePolicy must not be null");
        this.groundedGenerator = Objects.requireNonNull(
                groundedGenerator, "groundedGenerator must not be null");
        this.citationValidator = Objects.requireNonNull(
                citationValidator, "citationValidator must not be null");
        this.provenanceAssembler = Objects.requireNonNull(
                provenanceAssembler, "provenanceAssembler must not be null");
    }

    public PipelineResult execute(String question, String email) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        InterpretedQuery interpretedQuery = new InterpretedQuery(question);
        SourcePlan sourcePlan = sourcePlanner.plan(interpretedQuery);
        RetrievalResult retrieval = retrievalOrchestrator.retrieve(
                sourcePlan, interpretedQuery, email);

        RerankResult rerankResult = reranker.rerank(
                question, retrieval.evidence(), null);
        ContextBuilder.ContextBuilderConfig contextConfig =
                new ContextBuilder.ContextBuilderConfig(
                        null, null, sourcePlan.mixedSource(), false, Optional.of(sourcePlan));
        ContextBuilder.ConstructionResult construction = contextBuilder.build(
                question, retrieval.evidence(), Optional.of(rerankResult), contextConfig);

        List<ContextPiece> contextPieces = List.copyOf(construction.pieces());
        List<Evidence> usableEvidence = contextPieces.stream()
                .map(ContextPiece::evidence)
                .toList();
        EvidencePolicyDecision decision = evidencePolicy.evaluate(
                question, usableEvidence, sourcePlan, construction.droppedEvidence());

        Optional<ModelKnowledgeMetadata> modelKnowledge = decision.modelKnowledgeAllowed()
                ? Optional.of(new ModelKnowledgeMetadata(
                usableEvidence.isEmpty()
                        ? "General knowledge response permitted by the source plan"
                        : "Supplementary general knowledge permitted by the source plan",
                usableEvidence.isEmpty()
                        ? ModelKnowledgeMetadata.UsageMode.INDEPENDENT
                        : ModelKnowledgeMetadata.UsageMode.SUPPLEMENTAL))
                : Optional.empty();

        GenerationResult generation = groundedGenerator.generate(
                new GroundedGenerator.GenerationRequest(
                        question,
                        interpretedQuery.normalizedQuery(),
                        interpretedQuery.conversationContext(),
                        contextPieces,
                        Optional.of(new GroundedGenerator.GenerationPolicy(
                                true, true, Optional.empty())),
                        modelKnowledge,
                        sourcePlan,
                        decision));

        List<ProvenanceAssembler.PipelineFailure> failures = retrievalFailures(retrieval);
        Optional<ValidationResult> validation = Optional.empty();
        String finalAnswer = generation.refused()
                ? INSUFFICIENT_EVIDENCE_ANSWER
                : generation.answer();

        if (!generation.refused() && generation.answer() != null) {
            ValidationResult result = citationValidator.validate(
                    question, generation.answer(), generation.contextPieces(),
                    Optional.of(contextMetadata(contextPieces)));
            validation = Optional.of(result);
            if (!result.fullySupported()) {
                failures = new ArrayList<>(failures);
                failures.add(new ProvenanceAssembler.PipelineFailure(
                        ProvenanceAssembler.FailureKind.VALIDATION_FAILURE,
                        result.summary(), Optional.empty(), Optional.empty()));
                finalAnswer = INSUFFICIENT_EVIDENCE_ANSWER;
            }
        }

        ProvenanceAssembler.Provenance provenance = provenanceAssembler.assemble(
                sourcePlan, usableEvidence, construction.droppedEvidence(),
                Optional.of(decision), Optional.of(generation), validation, failures);

        RagResponse response = toResponse(finalAnswer, contextPieces, decision, generation);
        return new PipelineResult(response, provenance);
    }

    private static List<ProvenanceAssembler.PipelineFailure> retrievalFailures(
            RetrievalResult retrieval) {
        List<ProvenanceAssembler.PipelineFailure> failures = new ArrayList<>();
        for (KnowledgeSource source : retrieval.unavailableSources()) {
            failures.add(new ProvenanceAssembler.PipelineFailure(
                    ProvenanceAssembler.FailureKind.SOURCE_UNAVAILABLE,
                    "Knowledge source was unavailable: " + source,
                    Optional.of(source.name()), Optional.empty()));
        }
        return List.copyOf(failures);
    }

    private static String contextMetadata(List<ContextPiece> pieces) {
        return pieces.stream()
                .map(piece -> piece.evidence().source() + ":" + piece.evidence().id())
                .reduce((left, right) -> left + "," + right)
                .orElse("no-context");
    }

    private static RagResponse toResponse(
            String answer,
            List<ContextPiece> pieces,
            EvidencePolicyDecision decision,
            GenerationResult generation) {

        List<RagSource> documentSources = pieces.stream()
                .map(ContextPiece::evidence)
                .filter(DocumentEvidence.class::isInstance)
                .map(DocumentEvidence.class::cast)
                .map(ProductionRagPipeline::toSource)
                .toList();
        List<WebSearchResult> webSources = pieces.stream()
                .map(ContextPiece::evidence)
                .filter(WebEvidence.class::isInstance)
                .map(WebEvidence.class::cast)
                .map(ProductionRagPipeline::toWebSource)
                .toList();

        RagOrigin origin;
        if (answer == null || generation.refused()
                || answer.equals(INSUFFICIENT_EVIDENCE_ANSWER)) {
            return new RagResponse(
                    INSUFFICIENT_EVIDENCE_ANSWER,
                    List.of(), List.of(), RagOrigin.INSUFFICIENT_EVIDENCE);
        } else if (documentSources.isEmpty() && webSources.isEmpty()
                && decision.modelKnowledgeAllowed()) {
            origin = RagOrigin.MODEL_KNOWLEDGE;
        } else if (!documentSources.isEmpty() && !webSources.isEmpty()) {
            origin = RagOrigin.MIXED;
        } else if (!webSources.isEmpty()) {
            origin = RagOrigin.WEB;
        } else {
            origin = RagOrigin.DOCUMENTS;
        }
        return new RagResponse(answer, documentSources, webSources, origin);
    }

    private static RagSource toSource(DocumentEvidence evidence) {
        double similarity = evidence.similarity() == null ? 0.0 : evidence.similarity();
        return new RagSource(
                evidence.documentId(), evidence.filename(), evidence.chunkIndex(),
                similarity, evidence.keywordScore(), evidence.hybridScore());
    }

    private static WebSearchResult toWebSource(WebEvidence evidence) {
        return new WebSearchResult(
                evidence.title(),
                evidence.url(),
                evidence.content(),
                evidence.publisher(),
                evidence.domain(),
                evidence.path(),
                evidence.breadcrumb(),
                evidence.publishedDate());
    }

    /** Internal result retaining provenance without changing the public API. */
    public record PipelineResult(
            RagResponse response,
            ProvenanceAssembler.Provenance provenance) {
        public PipelineResult {
            Objects.requireNonNull(response, "response must not be null");
            Objects.requireNonNull(provenance, "provenance must not be null");
        }
    }
}


