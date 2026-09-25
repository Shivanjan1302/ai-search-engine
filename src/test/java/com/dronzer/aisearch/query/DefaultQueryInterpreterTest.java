package com.dronzer.aisearch.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultQueryInterpreterTest {

    private final DefaultQueryInterpreter interpreter = new DefaultQueryInterpreter();

    @Test
    void noContextPreservesStandaloneQuestion() {
        InterpretedQuery result = interpreter.interpret(
                "What is the termination clause?", Optional.empty());

        assertEquals("What is the termination clause?", result.originalQuery());
        assertEquals("What is the termination clause?", result.retrievalQuery());
        assertTrue(result.normalizedQuery().isEmpty());
        assertEquals(InterpretationStatus.UNCHANGED, result.interpretationStatus());
    }

    @Test
    void emptyContextPreservesStandaloneQuestion() {
        ConversationContext context = ConversationContext.empty();
        InterpretedQuery result = interpreter.interpret(
                "What are the main risks?", Optional.of(context));

        assertEquals("What are the main risks?", result.retrievalQuery());
        assertTrue(result.normalizedQuery().isEmpty());
        assertEquals(InterpretationStatus.UNCHANGED, result.interpretationStatus());
    }

    @Test
    void clearFollowUpCarriesSingleFilenameSubjectIntoRetrievalQuery() {
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("user", "What are the risks in contract.pdf?"),
                new ConversationContext.Turn("assistant", "The contract has several risks.")));

        InterpretedQuery result = interpreter.interpret(
                "What about termination?", Optional.of(context));

        assertEquals(InterpretationStatus.RESOLVED, result.interpretationStatus());
        assertEquals("What about termination?", result.originalQuery());
        assertTrue(result.retrievalQuery().contains("termination"));
        assertTrue(result.retrievalQuery().contains("contract.pdf"));
    }

    @Test
    void clearFollowUpUsesSubjectEstablishedInPreviousConversation() {
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("user", "Explain the leave policy in employee_handbook.pdf."),
                new ConversationContext.Turn("assistant", "The policy is described there.")));

        InterpretedQuery result = interpreter.interpret(
                "What about notice?", Optional.of(context));

        assertEquals(InterpretationStatus.RESOLVED, result.interpretationStatus());
        assertEquals("What about notice?", result.originalQuery());
        assertTrue(result.retrievalQuery().contains("notice"));
        assertTrue(result.retrievalQuery().contains("employee_handbook.pdf"));
    }

    @Test
    void implicitFollowUpWithoutConversationRemainsUnchanged() {
        InterpretedQuery result = interpreter.interpret(
                "What about termination?", Optional.empty());

        assertEquals(InterpretationStatus.UNCHANGED, result.interpretationStatus());
        assertEquals("What about termination?", result.retrievalQuery());
    }

    @Test
    void implicitFollowUpWithTwoDocumentSubjectsRemainsUnchanged() {
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("user", "Compare contract.pdf and policy.pdf.")));

        InterpretedQuery result = interpreter.interpret(
                "What about termination?", Optional.of(context));

        assertEquals(InterpretationStatus.UNCHANGED, result.interpretationStatus());
        assertEquals("What about termination?", result.retrievalQuery());
    }

    @Test
    void multiplePossibleSubjectsAcrossRecentTurnsRemainUnchanged() {
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("user", "Explain contract.pdf."),
                new ConversationContext.Turn("assistant", "The agreement is summarised."),
                new ConversationContext.Turn("user", "Explain employee_handbook.pdf."),
                new ConversationContext.Turn("assistant", "The policy is summarised.")));

        InterpretedQuery result = interpreter.interpret(
                "What about termination?", Optional.of(context));

        assertEquals(InterpretationStatus.UNCHANGED, result.interpretationStatus());
        assertEquals("What about termination?", result.retrievalQuery());
    }

    @Test
    void clearPronounsResolveFromLatestAssistantSubject() {
        assertResolved("What about it?", "The termination clause is important.", "termination clause");
        assertResolved("What is this?", "The termination clause is important.", "termination clause");
        assertResolved("Explain that.", "The termination clause is important.", "termination clause");
    }

    @Test
    void clearPluralPronounsResolveFromEstablishedTopic() {
        assertResolved("What are these?", "The termination and liability clauses are important.", "clauses");
        assertResolved("What are those?", "The termination and liability clauses are important.", "clauses");
    }

    @Test
    void clearOrderedListResolvesFirstSecondAndPrevious() {
        ConversationContext context = context("The main risks are:\n1. Termination\n2. Liability\n3. Confidentiality");

        assertEquals("What about Termination?", interpreter.interpret(
                "What about the first one?", Optional.of(context)).retrievalQuery());
        assertEquals("What about Liability?", interpreter.interpret(
                "What about the second one?", Optional.of(context)).retrievalQuery());
    }

    @Test
    void clearDocumentOrderingResolvesWithoutIntroducingDatabaseIds() {
        ConversationContext context = context("Document A: Employment Agreement\nDocument B: Vendor Contract");
        InterpretedQuery result = interpreter.interpret(
                "What about the second document?", Optional.of(context));

        assertTrue(result.retrievalQuery().contains("Document B"));
        assertTrue(result.retrievalQuery().contains("Vendor Contract"));
        assertFalse(result.retrievalQuery().contains("documentId"));
    }

    @Test
    void ambiguousPronounAndOrdinalRemainUnchanged() {
        ConversationContext ambiguous = context("We discussed termination and liability.");

        assertEquals("What about it?", interpreter.interpret(
                "What about it?", Optional.of(ambiguous)).retrievalQuery());
        assertEquals("What about the second one?", interpreter.interpret(
                "What about the second one?", Optional.of(ambiguous)).retrievalQuery());
    }

    @Test
    void ambiguousPronounRemainsUnresolvedWithNoFabricatedQuery() {
        ConversationContext ambiguous = context(
                "Both the contract and the employment policy contain termination provisions.");

        InterpretedQuery result = interpreter.interpret("What about it?", Optional.of(ambiguous));

        assertEquals("What about it?", result.originalQuery());
        assertEquals("What about it?", result.retrievalQuery());
        assertTrue(result.normalizedQuery().isEmpty());
        assertEquals(InterpretationStatus.AMBIGUOUS, result.interpretationStatus());
    }

    @Test
    void ambiguousOrdinalWithoutOrderingRemainsUnresolved() {
        ConversationContext ambiguous = context(
                "We discussed the employment contract, the NDA, and the vendor policy.");

        InterpretedQuery result = interpreter.interpret(
                "What about the second document?", Optional.of(ambiguous));

        assertEquals("What about the second document?", result.retrievalQuery());
        assertTrue(result.normalizedQuery().isEmpty());
        assertEquals(InterpretationStatus.AMBIGUOUS, result.interpretationStatus());
    }

    @Test
    void explicitDocumentOrderingResolvesWithHighConfidence() {
        ConversationContext ordered = context(
                "Document A: Employment Contract\nDocument B: NDA");

        InterpretedQuery result = interpreter.interpret(
                "What does the second document say about termination?", Optional.of(ordered));

        assertTrue(result.normalizedQuery().isPresent());
        assertTrue(result.normalizedQuery().orElseThrow().contains("NDA"));
        assertEquals(InterpretationStatus.RESOLVED, result.interpretationStatus());
    }

    @Test
    void explicitOrdinalDeclarationResolvesWithoutGuessingFromAnUnorderedList() {
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("assistant",
                        "The main risks are termination, liability, confidentiality."),
                new ConversationContext.Turn("user", "The second one is liability.")));

        InterpretedQuery result = interpreter.interpret(
                "What about the second one?", Optional.of(context));

        assertEquals("What about liability?", result.retrievalQuery());
        assertEquals(InterpretationStatus.RESOLVED, result.interpretationStatus());
    }

    @Test
    void referenceWithoutContextIsUnchangedAndNeverInvented() {
        InterpretedQuery result = interpreter.interpret("What about it?", Optional.empty());

        assertEquals("What about it?", result.retrievalQuery());
        assertTrue(result.normalizedQuery().isEmpty());
        assertEquals(InterpretationStatus.UNCHANGED, result.interpretationStatus());
    }

    @Test
    void ambiguousOrdinalWithSeveralUnorderedDocumentsRemainsUnresolved() {
        ConversationContext ambiguous = context(
                "The documents are the Employment Contract, the NDA, and the vendor policy.");

        InterpretedQuery result = interpreter.interpret(
                "What about the second document?", Optional.of(ambiguous));

        assertEquals("What about the second document?", result.retrievalQuery());
        assertTrue(result.normalizedQuery().isEmpty());
        assertEquals(InterpretationStatus.AMBIGUOUS, result.interpretationStatus());
    }

    @Test
    void contextDoesNotChangeOriginalQuestionAndRetainsSameContext() {
        ConversationContext context = context("The termination clause is important.");
        InterpretedQuery result = interpreter.interpret("What about it?", Optional.of(context));

        assertEquals("What about it?", result.originalQuery());
        assertEquals(Optional.of(context), result.conversationContext());
    }

    private void assertResolved(String question, String assistant, String expectedTopic) {
        InterpretedQuery result = interpreter.interpret(
                question, Optional.of(context(assistant)));
        assertTrue(result.retrievalQuery().toLowerCase().contains(expectedTopic.toLowerCase()),
                result.retrievalQuery());
    }

    private static ConversationContext context(String assistant) {
        return ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("user", "Earlier question"),
                new ConversationContext.Turn("assistant", assistant)));
    }
}
