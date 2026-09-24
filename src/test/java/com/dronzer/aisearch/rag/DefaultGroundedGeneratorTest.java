package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Unit tests for the Phase 2B-5 grounded generation boundary. */
class DefaultGroundedGeneratorTest {
    private static final String QUERY = "What is the current policy?";

    private final EvidencePolicy policy = DefaultEvidencePolicy.create();

    @Test void documentGroundedGenerationPreservesDocumentIdentityAndProvenance() {
        CapturingModel model = new CapturingModel("Document answer [E1]");
        DocumentEvidence document = document();
        GenerationResult result = generator(model).generate(request(documentPlan(), List.of(document), true));

        assertThat(result.answer()).isEqualTo("Document answer [E1]");
        assertThat(result.refused()).isFalse();
        assertThat(result.contextPieces()).extracting(piece -> piece.evidence().id()).containsExactly("7::2");
        assertThat(model.prompt.userPrompt()).contains("SOURCE TYPE: PRIVATE_DOCUMENT", "E1", "7::2", "policy.pdf", "chunk 2");
        assertThat(model.prompt.userPrompt()).contains("DOCUMENT is REQUIRED", "Web evidence must not substitute");
    }

    @Test void webGroundedGenerationPreservesUrlTitlePublisherAndFreshnessBoundary() {
        CapturingModel model = new CapturingModel("Current answer [E1]");
        WebEvidence web = web();
        GenerationResult result = generator(model).generate(request(webPlan(), List.of(web), false));

        assertThat(result.contextPieces()).extracting(piece -> piece.evidence().id()).containsExactly("https://current.test");
        assertThat(model.prompt.userPrompt()).contains("SOURCE TYPE: WEB", "https://current.test", "Current title", "Current publisher");
        assertThat(model.prompt.userPrompt()).contains("WEB is REQUIRED", "freshness-sensitive/current");
    }

    @Test void mixedDocumentAndWebGenerationPreservesBothSourceBoundaries() {
        CapturingModel model = new CapturingModel("Mixed answer [E1] [E2]");
        GenerationResult result = generator(model).generate(request(mixedPlan(), List.of(document(), web()), true));

        assertThat(result.contextPieces()).hasSize(2);
        assertThat(model.prompt.userPrompt()).contains("DOCUMENT is REQUIRED", "WEB is REQUIRED", "mixed-source request");
        assertThat(model.prompt.userPrompt()).contains("PRIVATE_DOCUMENT", "WEB", "E1", "E2");
    }

    @Test void modelKnowledgeAllowedIsExplicitAndNeverBecomesRetrievedEvidence() {
        CapturingModel model = new CapturingModel("General answer from model knowledge");
        ModelKnowledgeMetadata metadata = new ModelKnowledgeMetadata(
                "General background", ModelKnowledgeMetadata.UsageMode.INDEPENDENT);
        GenerationResult result = generator(model).generate(request(
                generalPlan(true), List.of(), true, Optional.of(metadata)));

        assertThat(result.contextPieces()).isEmpty();
        assertThat(result.modelKnowledge()).contains(metadata);
        assertThat(model.prompt.userPrompt()).contains("MODEL KNOWLEDGE: ALLOWED", "not retrieved evidence", "never be assigned document or web provenance");
    }

    @Test void modelKnowledgeDisallowedIsExplicit() {
        CapturingModel model = new CapturingModel("No model claims");
        generator(model).generate(request(generalPlan(false), List.of(document()), false));

        assertThat(model.prompt.userPrompt()).contains("MODEL KNOWLEDGE: NOT ALLOWED", "Do not use or imply model knowledge");
    }

    @Test void requiredEvidenceMissingRefusesWithoutInvokingModel() {
        CapturingModel model = new CapturingModel("must not be used");
        GenerationResult result = generator(model).generate(request(documentPlan(), List.of(), false));

        assertThat(result.refused()).isTrue();
        assertThat(result.answer()).isNull();
        assertThat(result.refusalReason()).hasValueSatisfying(reason -> assertThat(reason).contains("INSUFFICIENT"));
        assertThat(model.invocations).isZero();
    }

    @Test void optionalEvidenceMissingWithModelKnowledgeAllowedDoesNotForceRefusal() {
        CapturingModel model = new CapturingModel("A general answer");
        GenerationResult result = generator(model).generate(request(generalPlan(true), List.of(), true));

        assertThat(result.refused()).isFalse();
        assertThat(result.answer()).isEqualTo("A general answer");
        assertThat(model.prompt.userPrompt()).contains("No retrievable source is required", "do not force a refusal");
    }

    @Test void emptyContextWithModelKnowledgeDisallowedIsRefused() {
        CapturingModel model = new CapturingModel("must not be used");
        GenerationResult result = generator(model).generate(request(generalPlan(false), List.of(), false));

        assertThat(result.refused()).isTrue();
        assertThat(model.invocations).isZero();
    }

    @Test void originalQueryAndNormalizedQueryArePreservedSeparately() {
        CapturingModel model = new CapturingModel("Answer");
        GroundedGenerator.GenerationRequest request = new GroundedGenerator.GenerationRequest(
                QUERY, Optional.of("normalized query"), Optional.empty(), List.<ContextPiece>of(new ContextPiece(document())),
                Optional.empty(), Optional.empty(), documentPlan(),
                decision(documentPlan(), List.of(document())));

        generator(model).generate(request);

        assertThat(model.prompt.userPrompt()).contains("USER QUERY:\n" + QUERY,
                "NORMALIZED QUERY (context only; original query remains authoritative):\nnormalized query");
    }

    @Test void citationPreparationMetadataRetainsEverySuppliedPieceAndDecision() {
        CapturingModel model = new CapturingModel("Answer");
        GenerationResult result = generator(model).generate(request(mixedPlan(), List.of(document(), web()), true));

        assertThat(result.contextPieces()).extracting(ContextPiece::evidence).containsExactly(document(), web());
        assertThat(result.evidencePolicyDecision()).hasValue(decision(mixedPlan(), List.of(document(), web()), true));
        assertThat(result.generationMetadata()).hasValue("policy=evidence-policy; phase=2B-5");
    }

    @Test void modelInvocationFailureIsWrappedWithoutLeakingProviderDetails() {
        GenerationModel model = prompt -> { throw new IllegalStateException("secret upstream detail"); };
        assertThatThrownBy(() -> generator(model).generate(request(documentPlan(), List.of(document()), true)))
                .isInstanceOf(GenerationException.class)
                .hasMessage("generation model invocation failed")
                .hasMessageNotContaining("secret upstream detail");
    }

    @Test void malformedModelResponseIsRejected() {
        assertThatThrownBy(() -> generator(prompt -> "  ").generate(request(documentPlan(), List.of(document()), true)))
                .isInstanceOf(GenerationException.class)
                .hasMessage("generation model returned a malformed empty response");
    }

    @Test void nullAndBlankInputsAreRejected() {
        DefaultGroundedGenerator generator = generator(prompt -> "answer");
        assertThatThrownBy(() -> generator.generate(null)).isInstanceOf(GenerationException.class);
        assertThatThrownBy(() -> generator.generate(request(documentPlan(), List.of(document()), true, Optional.empty(), "  ")))
                .isInstanceOf(GenerationException.class).hasMessage("userQuery must not be null or blank");
        assertThatThrownBy(() -> generator.generate(new GroundedGenerator.GenerationRequest(
                QUERY, Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), null, null)))
                .isInstanceOf(GenerationException.class).hasMessage("sourcePlan must not be null");
        assertThatThrownBy(() -> generator.generate(new GroundedGenerator.GenerationRequest(
                QUERY, Optional.empty(), Optional.empty(), null, Optional.empty(), Optional.empty(), documentPlan(),
                decision(documentPlan(), List.of(document())))))
                .isInstanceOf(GenerationException.class).hasMessage("contextPieces must not be null");
        assertThatThrownBy(() -> generator.generate(new GroundedGenerator.GenerationRequest(
                QUERY, Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), documentPlan(), null)))
                .isInstanceOf(GenerationException.class).hasMessage("evidencePolicyDecision must not be null");
    }

    @Test void modelKnowledgeCannotBeInsertedIntoRetrievedContext() {
        Evidence modelEvidence = new Evidence() {
            public KnowledgeSource source() { return KnowledgeSource.MODEL_KNOWLEDGE; }
            public String content() { return "invented memory"; }
            public String id() { return "memory"; }
            public String provenance() { return "model"; }
            public Double score() { return null; }
        };
        List<ContextPiece> pieces = List.of(new ContextPiece(modelEvidence));
        assertThatThrownBy(() -> generator(prompt -> "answer").generate(
                new GroundedGenerator.GenerationRequest(QUERY, Optional.empty(), Optional.empty(), pieces,
                        Optional.empty(), Optional.empty(), documentPlan(),
                        new EvidencePolicyDecision(true, "test", List.of(KnowledgeSource.DOCUMENT), false, false,
                                Optional.of("test"), true))))
                .isInstanceOf(GenerationException.class)
                .hasMessage("model knowledge must not be represented as retrieved evidence");
    }

    @Test void missingModelIsRejected() {
        assertThatThrownBy(() -> new DefaultGroundedGenerator(null))
                .isInstanceOf(GenerationException.class).hasMessage("generation model must not be null");
    }

    @Test void promptConstructionIsDeterministic() {
        CapturingModel first = new CapturingModel("one");
        CapturingModel second = new CapturingModel("two");
        GroundedGenerator.GenerationRequest request = request(mixedPlan(), List.of(document(), web()), true);

        generator(first).generate(request);
        generator(second).generate(request);

        assertThat(first.prompt).isEqualTo(second.prompt);
        assertThat(first.prompt.systemPrompt()).contains("You are Dronzer's grounded answer generator",
                "Do not fabricate facts", "Never claim to have accessed a source that was not supplied");
    }

    @Test void generatorHasNoRepositoryOrRetrievalProviderDependency() {
        for (Field field : DefaultGroundedGenerator.class.getDeclaredFields()) {
            String type = field.getType().getSimpleName();
            assertThat(type).doesNotContain("Repository", "Service", "Client", "Template", "EntityManager");
        }
        assertThat(Modifier.isFinal(DefaultGroundedGenerator.class.getModifiers())).isTrue();
    }

    private DefaultGroundedGenerator generator(GenerationModel model) {
        return new DefaultGroundedGenerator(model);
    }

    private GroundedGenerator.GenerationRequest request(
            SourcePlan plan, Collection<Evidence> evidence, boolean modelAllowed) {
        return request(plan, evidence, modelAllowed, Optional.empty());
    }

    private GroundedGenerator.GenerationRequest request(
            SourcePlan plan, Collection<? extends Evidence> evidence, boolean modelAllowed,
            Optional<ModelKnowledgeMetadata> modelKnowledge) {
        List<Evidence> supplied = new ArrayList<>(evidence);
        List<ContextPiece> pieces = supplied.stream().map(ContextPiece::new).toList();
        return new GroundedGenerator.GenerationRequest(
                QUERY, Optional.empty(), Optional.empty(), pieces, Optional.empty(), modelKnowledge,
                plan, decision(plan, supplied, modelAllowed));
    }

    private GroundedGenerator.GenerationRequest request(
            SourcePlan plan, Collection<Evidence> evidence, boolean modelAllowed,
            Optional<ModelKnowledgeMetadata> modelKnowledge, String query) {
        List<Evidence> supplied = new ArrayList<>(evidence);
        List<ContextPiece> pieces = supplied.stream().map(ContextPiece::new).toList();
        return new GroundedGenerator.GenerationRequest(
                query, Optional.empty(), Optional.empty(), pieces, Optional.empty(), modelKnowledge,
                plan, decision(plan, supplied, modelAllowed));
    }

    private EvidencePolicyDecision decision(SourcePlan plan, Collection<Evidence> evidence) {
        List<Evidence> supplied = new ArrayList<>(evidence);
        return policy.evaluate(QUERY, supplied, plan, List.of());
    }

    private EvidencePolicyDecision decision(
            SourcePlan plan, Collection<Evidence> evidence, boolean modelAllowed) {
        EvidencePolicyDecision base = decision(plan, evidence);
        if (modelAllowed) {
            return new EvidencePolicyDecision(true, "test model knowledge permitted", base.sourcesWithEvidence(),
                    false, false, Optional.of("test"), true);
        }
        if (!evidence.isEmpty()) {
            return new EvidencePolicyDecision(true, "SUFFICIENT_EVIDENCE: test retrieved evidence",
                    base.sourcesWithEvidence(), false, false, Optional.of("test"), false);
        }
        return new EvidencePolicyDecision(false, "INSUFFICIENT_NO_EVIDENCE: model knowledge not permitted",
                base.sourcesWithEvidence(), false, false, Optional.of("test"), false);
    }

    private static SourcePlan documentPlan() {
        return new SourcePlan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)), false, false,
                EvidenceRequirement.STRICT, FallbackPolicy.FAIL_FAST);
    }

    private static SourcePlan webPlan() {
        return new SourcePlan(List.of(SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE).withPermitted(false)), false, true,
                EvidenceRequirement.STRICT, FallbackPolicy.FAIL_FAST);
    }

    private static SourcePlan mixedPlan() {
        return new SourcePlan(List.of(SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE)), true, true,
                EvidenceRequirement.STRICT, FallbackPolicy.FAIL_FAST);
    }

    private static SourcePlan generalPlan(boolean modelAllowed) {
        return new SourcePlan(List.of(SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE).withPermitted(modelAllowed)),
                false, false, EvidenceRequirement.MODERATE, FallbackPolicy.FAIL_FAST);
    }

    private static DocumentEvidence document() {
        return new DocumentEvidence(7L, "policy.pdf", 2, "Leave policy content",
                0.8, 0.4, 0.7, 0.8, "hybrid");
    }

    private static WebEvidence web() {
        return new WebEvidence("https://current.test", "Current title", "Current publisher",
                "Current external content", 0.7, "web");
    }

    private static final class CapturingModel implements GenerationModel {
        private final String answer;
        private GenerationPrompt prompt;
        private int invocations;

        private CapturingModel(String answer) {
            this.answer = answer;
        }

        @Override
        public String generate(GenerationPrompt prompt) {
            this.prompt = prompt;
            invocations++;
            return answer;
        }
    }
}

