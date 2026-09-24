package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure in-memory tests for the evidence policy boundary. */
class DefaultEvidencePolicyTest {

    private static final String QUERY = "What does the policy say?";
    private final EvidencePolicy policy = DefaultEvidencePolicy.create();

    @Test void documentRequired_withDocumentEvidence_isSufficient() {
        EvidencePolicyDecision decision = evaluate(documentPlan(), List.of(document()), List.of());
        assertSufficientWithRetrievedEvidence(decision, KnowledgeSource.DOCUMENT);
    }

    @Test void documentRequired_withoutDocumentEvidence_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(documentPlan(), List.of(), List.of());
        assertRequiredMissing(decision, KnowledgeSource.DOCUMENT);
    }

    @Test void documentRequired_withOnlyWebEvidence_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(documentPlan(), List.of(web()), List.of());
        assertRequiredMissing(decision, KnowledgeSource.DOCUMENT);
        assertEquals(List.of(KnowledgeSource.WEB), decision.sourcesWithEvidence());
    }

    @Test void webRequired_withWebEvidence_isSufficient() {
        EvidencePolicyDecision decision = evaluate(webPlan(), List.of(web()), List.of());
        assertSufficientWithRetrievedEvidence(decision, KnowledgeSource.WEB);
    }

    @Test void webRequired_withoutWebEvidence_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(webPlan(), List.of(), List.of());
        assertRequiredMissing(decision, KnowledgeSource.WEB);
    }

    @Test void webRequired_withOnlyDocumentEvidence_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(webPlan(), List.of(document()), List.of());
        assertRequiredMissing(decision, KnowledgeSource.WEB);
        assertEquals(List.of(KnowledgeSource.DOCUMENT), decision.sourcesWithEvidence());
    }

    @Test void mixedRequired_withBothEvidenceTypes_isSufficient() {
        EvidencePolicyDecision decision = evaluate(mixedPlan(), List.of(document(), web()), List.of());
        assertTrue(decision.sufficient());
        assertFalse(decision.requiredSourceUnavailable());
        assertEquals(List.of(KnowledgeSource.DOCUMENT, KnowledgeSource.WEB),
                decision.sourcesWithEvidence());
    }

    @Test void mixedRequired_withDocumentMissing_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(mixedPlan(), List.of(web()), List.of());
        assertRequiredMissing(decision, KnowledgeSource.DOCUMENT);
    }

    @Test void mixedRequired_withWebMissing_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(mixedPlan(), List.of(document()), List.of());
        assertRequiredMissing(decision, KnowledgeSource.WEB);
    }

    @Test void mixedRequired_withBothMissing_isInsufficient() {
        EvidencePolicyDecision decision = evaluate(mixedPlan(), List.of(), List.of());
        assertFalse(decision.sufficient());
        assertTrue(decision.requiredSourceUnavailable());
        assertTrue(decision.reason().contains("[DOCUMENT, WEB]"));
    }

    @Test void generalKnowledge_noRetrievedEvidence_modelAllowed_isSufficient() {
        EvidencePolicyDecision decision = evaluate(generalPlan(true), List.of(), List.of());
        assertTrue(decision.sufficient());
        assertTrue(decision.modelKnowledgeAllowed());
        assertEquals(List.of(), decision.sourcesWithEvidence());
        assertTrue(decision.reason().startsWith("SUFFICIENT_MODEL_KNOWLEDGE:"));
    }

    @Test void generalKnowledge_withRetrievedEvidence_reportsRetrievedSource() {
        EvidencePolicyDecision decision = evaluate(generalPlan(true), List.of(web()), List.of());
        assertSufficientWithRetrievedEvidence(decision, KnowledgeSource.WEB);
        assertTrue(decision.modelKnowledgeAllowed());
    }

    @Test void modelKnowledgeNotAllowed_noEvidence_isExplicitlyInsufficient() {
        EvidencePolicyDecision decision = evaluate(generalPlan(false), List.of(), List.of());
        assertFalse(decision.sufficient());
        assertFalse(decision.modelKnowledgeAllowed());
        assertTrue(decision.reason().startsWith("INSUFFICIENT_NO_EVIDENCE:"));
    }

    @Test void optionalEvidenceMissing_doesNotBlockWhenModelKnowledgeAllowed() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                modelAllowed()), false, false);
        EvidencePolicyDecision decision = evaluate(plan, List.of(), List.of());
        assertTrue(decision.sufficient());
        assertFalse(decision.requiredSourceUnavailable());
    }

    @Test void optionalEvidencePresent_isReportedWithoutBecomingRequired() {
        EvidencePolicyDecision decision = evaluate(
                plan(List.of(SourceRequirement.optional(KnowledgeSource.DOCUMENT)),
                        false, false),
                List.of(document()), List.of());
        assertTrue(decision.sufficient());
        assertFalse(decision.requiredSourceUnavailable());
        assertEquals(List.of(KnowledgeSource.DOCUMENT), decision.sourcesWithEvidence());
    }

    @Test void droppedEvidence_doesNotSatisfyRequiredSource() {
        EvidencePolicyDecision decision = evaluate(documentPlan(), List.of(), List.of(document()));
        assertRequiredMissing(decision, KnowledgeSource.DOCUMENT);
        assertTrue(decision.coverageSummary().orElseThrow().contains("dropped=1"));
    }

    @Test void unplannedEvidence_doesNotSilentlyBecomeAnAllowedFallback() {
        SourcePlan plan = plan(List.of(
                SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE).withPermitted(false)),
                false, false);
        EvidencePolicyDecision decision = evaluate(plan, List.of(document(), web()), List.of());
        assertFalse(decision.sufficient());
        assertEquals(List.of(), decision.sourcesWithEvidence());
    }

    @Test void modelKnowledge_isNeverAcceptedAsEvidence() {
        Evidence model = mock(Evidence.class);
        when(model.source()).thenReturn(KnowledgeSource.MODEL_KNOWLEDGE);
        when(model.id()).thenReturn("model-memory");
        when(model.content()).thenReturn("parametric answer");
        EvidencePolicyException failure = assertThrows(EvidencePolicyException.class,
                () -> evaluate(generalPlan(true), List.of(model), List.of()));
        assertTrue(failure.getMessage().contains("must not represent model knowledge as evidence"));
        verify(model, never()).score();
    }

    @Test void evidenceScores_areNeverConsulted() {
        Evidence document = mock(Evidence.class);
        when(document.source()).thenReturn(KnowledgeSource.DOCUMENT);
        when(document.id()).thenReturn("doc-1");
        when(document.content()).thenReturn("policy text");
        EvidencePolicyDecision decision = evaluate(documentPlan(), List.of(document), List.of());
        assertTrue(decision.sufficient());
        verify(document, never()).score();
    }

    @Test void identicalInputs_produceIdenticalDecisions() {
        SourcePlan plan = mixedPlan();
        List<Evidence> evidence = List.of(web(), document());
        EvidencePolicyDecision first = evaluate(plan, evidence, List.of());
        EvidencePolicyDecision second = DefaultEvidencePolicy.create().evaluate(
                QUERY, List.of(web(), document()), plan, List.of());
        assertEquals(first, second);
        assertEquals(first.reason(), second.reason());
        assertEquals(first.sourcesWithEvidence(), second.sourcesWithEvidence());
        assertEquals(first.coverageSummary(), second.coverageSummary());
    }

    @Test void decisionOrdering_followsSourcePlanNotEvidenceInsertionOrder() {
        EvidencePolicyDecision decision = evaluate(mixedPlan(),
                List.of(web(), document()), List.of());
        assertEquals(List.of(KnowledgeSource.DOCUMENT, KnowledgeSource.WEB),
                decision.sourcesWithEvidence());
        assertThrows(UnsupportedOperationException.class,
                () -> decision.sourcesWithEvidence().add(KnowledgeSource.WEB));
    }

    @Test void nullAndBlankInputs_failExplicitly() {
        assertThrows(EvidencePolicyException.class,
                () -> policy.evaluate(null, List.of(), generalPlan(true), List.of()));
        assertThrows(EvidencePolicyException.class,
                () -> policy.evaluate("  ", List.of(), generalPlan(true), List.of()));
        assertThrows(EvidencePolicyException.class,
                () -> policy.evaluate(QUERY, null, generalPlan(true), List.of()));
        assertThrows(EvidencePolicyException.class,
                () -> policy.evaluate(QUERY, List.of(), null, List.of()));
        assertThrows(EvidencePolicyException.class,
                () -> policy.evaluate(QUERY, List.of(), generalPlan(true), null));
    }

    @Test void nullAndMalformedEvidence_failExplicitly() {
        List<Evidence> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(EvidencePolicyException.class,
                () -> evaluate(generalPlan(true), withNull, List.of()));

        Evidence nullSource = mock(Evidence.class);
        when(nullSource.source()).thenReturn(null);
        assertThrows(EvidencePolicyException.class,
                () -> evaluate(generalPlan(true), List.of(nullSource), List.of()));

        Evidence blankContent = mock(Evidence.class);
        when(blankContent.source()).thenReturn(KnowledgeSource.DOCUMENT);
        when(blankContent.id()).thenReturn("doc-1");
        when(blankContent.content()).thenReturn("  ");
        assertThrows(EvidencePolicyException.class,
                () -> evaluate(documentPlan(), List.of(blankContent), List.of()));

        Evidence accessorFailure = mock(Evidence.class);
        when(accessorFailure.source()).thenThrow(new IllegalStateException("broken"));
        EvidencePolicyException failure = assertThrows(EvidencePolicyException.class,
                () -> evaluate(generalPlan(true), List.of(accessorFailure), List.of()));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test void inconsistentSourceRequirements_failExplicitly() {
        SourcePlan duplicate = plan(List.of(
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.required(KnowledgeSource.DOCUMENT)), false, false);
        assertThrows(EvidencePolicyException.class,
                () -> evaluate(duplicate, List.of(document()), List.of()));

        SourcePlan nullLevel = plan(List.of(
                new SourceRequirement(KnowledgeSource.DOCUMENT, null)), false, false);
        assertThrows(EvidencePolicyException.class,
                () -> evaluate(nullLevel, List.of(document()), List.of()));

        SourcePlan requiredButDisallowed = plan(List.of(
                SourceRequirement.required(KnowledgeSource.WEB).withPermitted(false)),
                false, true);
        assertThrows(EvidencePolicyException.class,
                () -> evaluate(requiredButDisallowed, List.of(web()), List.of()));
    }

    @Test void policy_isStatelessAndHasNoRetrievalProviderOrDatabaseDependencies() {
        assertTrue(Modifier.isFinal(DefaultEvidencePolicy.class.getModifiers()));
        Constructor<?>[] constructors = DefaultEvidencePolicy.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());

        for (Field field : DefaultEvidencePolicy.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    "policy must not hold state: " + field.getName());
            String type = field.getType().getSimpleName();
            assertFalse(type.endsWith("Client") || type.endsWith("Repository")
                            || type.endsWith("Service") || type.endsWith("Template"),
                    "forbidden dependency: " + type);
        }
        for (Annotation annotation : DefaultEvidencePolicy.class.getAnnotations()) {
            assertFalse(annotation.annotationType().getName().startsWith("org.springframework"));
        }
    }

    private EvidencePolicyDecision evaluate(
            SourcePlan plan, List<Evidence> evidence, List<Evidence> dropped) {
        return policy.evaluate(QUERY, evidence, plan, dropped);
    }

    private static void assertSufficientWithRetrievedEvidence(
            EvidencePolicyDecision decision,
            KnowledgeSource source
    ) {
        assertTrue(decision.sufficient());
        assertFalse(decision.requiredSourceUnavailable());
        assertFalse(decision.conflictDetected());
        assertEquals(List.of(source), decision.sourcesWithEvidence());
        assertTrue(decision.reason().startsWith("SUFFICIENT_EVIDENCE:"));
    }

    private static void assertRequiredMissing(
            EvidencePolicyDecision decision,
            KnowledgeSource source
    ) {
        assertFalse(decision.sufficient());
        assertTrue(decision.requiredSourceUnavailable());
        assertTrue(decision.reason().startsWith("INSUFFICIENT_REQUIRED_EVIDENCE:"));
        assertTrue(decision.reason().contains(source.name()));
    }

    private static SourcePlan documentPlan() {
        return plan(List.of(
                SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                modelAllowed()), false, false);
    }

    private static SourcePlan webPlan() {
        return plan(List.of(
                SourceRequirement.required(KnowledgeSource.WEB),
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                modelDisallowed()), false, true);
    }

    private static SourcePlan mixedPlan() {
        return plan(List.of(
                SourceRequirement.required(KnowledgeSource.DOCUMENT),
                SourceRequirement.required(KnowledgeSource.WEB),
                modelAllowed()), true, true);
    }

    private static SourcePlan generalPlan(boolean modelAllowed) {
        return plan(List.of(
                SourceRequirement.optional(KnowledgeSource.DOCUMENT),
                SourceRequirement.optional(KnowledgeSource.WEB),
                modelAllowed ? modelAllowed() : modelDisallowed()), false, false);
    }

    private static SourceRequirement modelAllowed() {
        return SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE).withPermitted(true);
    }

    private static SourceRequirement modelDisallowed() {
        return SourceRequirement.optional(KnowledgeSource.MODEL_KNOWLEDGE).withPermitted(false);
    }

    private static SourcePlan plan(
            List<SourceRequirement> sources, boolean mixed, boolean freshness) {
        return new SourcePlan(sources, mixed, freshness,
                EvidenceRequirement.MODERATE, FallbackPolicy.FAIL_FAST);
    }

    private static DocumentEvidence document() {
        return new DocumentEvidence(7L, "policy.pdf", 2, "Leave policy content",
                0.8, 0.4, 0.7, 0.8, "hybrid");
    }

    private static WebEvidence web() {
        return new WebEvidence("https://example.test/current", "Current", "Publisher",
                "Current external content", 0.7, "web");
    }
}
