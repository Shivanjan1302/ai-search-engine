package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import com.dronzer.aisearch.query.InterpretedQuery;
import com.dronzer.aisearch.query.QueryIntent;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSourcePlannerTest {

    private final SourcePlanner planner = DefaultSourcePlanner.create();

    private static Map<KnowledgeSource, RequirementLevel> levels(SourcePlan plan) {
        return plan.sources().stream()
                .collect(Collectors.toMap(
                        SourceRequirement::source,
                        SourceRequirement::level));
    }

    @Test
    void documentSpecificIntent_requiresDocumentAndNotWeb() {
        InterpretedQuery query = new InterpretedQuery("What does my uploaded document say about leave policy?")
                .withIntent(QueryIntent.DOCUMENT_SPECIFIC);

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.WEB));
        assertFalse(plan.mixedSource());
        assertFalse(plan.requiresFreshness());
        assertEquals(EvidenceRequirement.MODERATE, plan.evidenceRequirement());
        assertEquals(FallbackPolicy.FAIL_FAST, plan.fallbackPolicy());
        assertEquals(KnowledgeSource.DOCUMENT, plan.sources().get(0).source());
    }

    @Test
    void generalKnowledgeIntent_requiresNothingAndAllowsModelKnowledge() {
        InterpretedQuery query = new InterpretedQuery("What is Kubernetes?")
                .withIntent(QueryIntent.GENERAL_KNOWLEDGE);

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.WEB));
        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.MODEL_KNOWLEDGE));
        assertFalse(plan.mixedSource());
        assertFalse(plan.requiresFreshness());
        assertEquals(EvidenceRequirement.RELAXED, plan.evidenceRequirement());
        assertEquals(FallbackPolicy.FAIL_FAST, plan.fallbackPolicy());
    }

    @Test
    void currentInformationIntent_requiresWeb() {
        InterpretedQuery query = new InterpretedQuery("What happened today in tech news?")
                .withIntent(QueryIntent.CURRENT_INFORMATION);

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.WEB));
        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.DOCUMENT));
        assertTrue(plan.requiresFreshness());
        assertFalse(plan.mixedSource());
        assertEquals(EvidenceRequirement.MODERATE, plan.evidenceRequirement());
        assertEquals(FallbackPolicy.DEGRADE_GRADUALLY, plan.fallbackPolicy());
    }

    @Test
    void freshnessHeuristic_requiresWebWhenNoSignals() {
        InterpretedQuery query = new InterpretedQuery("What is the latest price of gold right now?");

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.WEB));
        assertTrue(plan.requiresFreshness());
    }

    @Test
    void mixedIntent_requiresDocumentAndWeb() {
        InterpretedQuery query = new InterpretedQuery(
                "Based on my company document, compare it with current industry standards.")
                .withIntent(QueryIntent.MIXED_SOURCE);

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.WEB));
        assertTrue(plan.mixedSource());
        assertTrue(plan.requiresFreshness());
        assertEquals(EvidenceRequirement.MODERATE, plan.evidenceRequirement());
    }

    @Test
    void mixedExplicitSignals_requireDocumentAndWeb() {
        InterpretedQuery query = new InterpretedQuery("Compare my leave policy with the latest labor law updates.")
                .withDocumentSpecific(true)
                .withRequiresFreshness(true);

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.REQUIRED, levels.get(KnowledgeSource.WEB));
        assertTrue(plan.mixedSource());
        assertTrue(plan.requiresFreshness());
    }

    @Test
    void documentSpecificTrueSignal_overridesGeneralText() {
        InterpretedQuery query = new InterpretedQuery("What is Kubernetes?")
                .withDocumentSpecific(true);

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.REQUIRED, levels(plan).get(KnowledgeSource.DOCUMENT));
        assertFalse(plan.requiresFreshness());
    }

    @Test
    void documentSpecificFalseSignal_suppressesDocumentHeuristic() {
        // Classifier says NOT document-specific even though the text mentions documents.
        InterpretedQuery query = new InterpretedQuery("What does my document say about leave policy?")
                .withDocumentSpecific(false);

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.OPTIONAL, levels(plan).get(KnowledgeSource.DOCUMENT));
        assertFalse(plan.mixedSource());
    }

    @Test
    void emptySignals_useHeuristicsOnly() {
        // No signals: "my uploaded document" text alone drives DOCUMENT.
        InterpretedQuery query = new InterpretedQuery("Summarize my uploaded document on benefits.");

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.REQUIRED, levels(plan).get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.OPTIONAL, levels(plan).get(KnowledgeSource.WEB));
    }

    @Test
    void unknownIntentWithNoSignals_isGeneral() {
        InterpretedQuery query = new InterpretedQuery("Explain vector databases.")
                .withIntent(QueryIntent.UNKNOWN);

        SourcePlan plan = planner.plan(query);
        Map<KnowledgeSource, RequirementLevel> levels = levels(plan);

        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.OPTIONAL, levels.get(KnowledgeSource.WEB));
        assertFalse(plan.mixedSource());
        assertFalse(plan.requiresFreshness());
    }

    @Test
    void explicitFalseFreshness_suppressesFreshnessHeuristic() {
        InterpretedQuery query = new InterpretedQuery("What is the latest price of gold right now?")
                .withRequiresFreshness(false);

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.OPTIONAL, levels(plan).get(KnowledgeSource.WEB));
        assertFalse(plan.requiresFreshness());
    }

    @Test
    void webRequiredTrueSignal_requiresWeb() {
        InterpretedQuery query = new InterpretedQuery("What is Kubernetes?")
                .withWebRequired(true);

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.REQUIRED, levels(plan).get(KnowledgeSource.WEB));
        assertTrue(plan.requiresFreshness());
    }

    @Test
    void outputIsDeterministic() {
        InterpretedQuery query = new InterpretedQuery("Based on my company document, compare with current standards.")
                .withIntent(QueryIntent.MIXED_SOURCE);

        SourcePlan first = planner.plan(query);
        SourcePlan second = planner.plan(query);

        assertEquals(first, second);
        assertEquals(DefaultSourcePlanner.create().plan(query), first);
    }

    @Test
    void planHasNoExternalDependencies() {
        // No Spring, no DB, no HTTP: planner is a plain final class with a factory.
        assertTrue(java.lang.reflect.Modifier.isFinal(DefaultSourcePlanner.class.getModifiers()));
        for (var field : DefaultSourcePlanner.class.getDeclaredFields()) {
            assertTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()),
                    "planner must hold no instance state: " + field.getName());
        }
        for (var ctor : DefaultSourcePlanner.class.getDeclaredConstructors()) {
            assertEquals(0, ctor.getParameterCount());
        }
    }

    @Test
    void modelKnowledgeDisallowed_isHonoured() {
        InterpretedQuery query = new InterpretedQuery("What is Kubernetes?")
                .withIntent(QueryIntent.GENERAL_KNOWLEDGE)
                .withModelKnowledgeAllowed(false);

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.OPTIONAL, levels(plan).get(KnowledgeSource.MODEL_KNOWLEDGE));
        assertTrue(plan.sources().stream()
                .filter(r -> r.source() == KnowledgeSource.MODEL_KNOWLEDGE)
                .findFirst().orElseThrow().reason().contains("disallowed"));
    }

    @Test
    void nullQuery_throwsPlanningException() {
        assertThrows(SourcePlanningException.class, () -> planner.plan(null));
    }

    @Test
    void blankQuery_throwsPlanningException() {
        assertThrows(SourcePlanningException.class, () -> planner.plan(new InterpretedQuery("  ")));
    }

    @Test
    void mixedSourceFlag_derivedFromRequirementsNotSignal() {
        // Provisional mixedSource=false must NOT leak into the plan when
        // the actual requirements demand both sources.
        InterpretedQuery query = new InterpretedQuery("Compare my policy with the latest updates.")
                .withDocumentSpecific(true)
                .withRequiresFreshness(true)
                .withMixedSource(false);

        SourcePlan plan = planner.plan(query);

        assertTrue(plan.mixedSource());
    }

    @Test
    void documentIntentWithFreshSignal_blendsToMixed() {
        InterpretedQuery query = new InterpretedQuery("What does my document say about the latest policy changes?")
                .withIntent(QueryIntent.DOCUMENT_SPECIFIC)
                .withRequiresFreshness(true);

        SourcePlan plan = planner.plan(query);

        assertEquals(RequirementLevel.REQUIRED, levels(plan).get(KnowledgeSource.DOCUMENT));
        assertEquals(RequirementLevel.REQUIRED, levels(plan).get(KnowledgeSource.WEB));
        assertTrue(plan.mixedSource());
        assertTrue(plan.requiresFreshness());
    }
}
