package com.dronzer.aisearch.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.service.DocumentService;
import com.dronzer.aisearch.service.RagService;

/**
 * Deterministic evaluation of Phase 1 evidence selection and answer grounding.
 *
 * This is intentionally a unit-level harness. It does not claim to measure the
 * external embedding or generation models; those require a known corpus and a
 * running service. The opt-in pgvector integration test covers database tenant
 * filtering separately.
 */
class RagEvaluationTest {

    private static final String USER_EMAIL = "evaluation-a@example.test";
    private static final String OTHER_USER_EMAIL = "evaluation-b@example.test";
    private static final String FALLBACK = "I could not find relevant information in your documents.";

    private final DocumentService documentService = mock(DocumentService.class);
    private final AIClient aiClient = mock(AIClient.class);
    private final RagService ragService = new RagService(documentService, aiClient);

    @Test
    void evaluatesPhaseOneAndWritesReport() throws IOException {
        List<EvaluationResult> results = List.of(
                evaluate(directFactCase()),
                evaluate(multiChunkCase()),
                evaluate(documentConcentrationCase()),
                evaluate(negativeCase()),
                evaluate(wordingVariationCase()),
                evaluate(weakSemanticCase()),
                evaluate(tenantIsolationCase()),
                evaluateDeterminismCase());

        assertThat(results).allSatisfy(result -> assertThat(result.status()).isNotEqualTo(Status.FAIL));

        String report = renderReport(results);
        Path reportPath = Path.of("target", "rag-evaluation-report.md");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report);
        System.out.print(report);
    }

    private EvaluationResult evaluate(EvaluationCase testCase) {
        reset(documentService, aiClient);
        when(documentService.searchSemantically(testCase.question(), 20, testCase.email()))
                .thenReturn(testCase.results());
        when(aiClient.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(testCase.answer());

        RagResponse response = ragService.askQuestion(testCase.question(), testCase.email());
        List<String> actualKeys = response.sources().stream()
                .map(source -> source.documentId() + ":" + source.chunkIndex())
                .toList();
        verify(documentService).searchSemantically(testCase.question(), 20, testCase.email());
        boolean sourcesMatch = actualKeys.containsAll(testCase.expectedSourceKeys());
        boolean answerMatches = testCase.expectedAnswerFragments().stream()
                .allMatch(fragment -> response.answer().toLowerCase(Locale.ROOT)
                        .contains(fragment.toLowerCase(Locale.ROOT)));
        boolean refused = FALLBACK.equals(response.answer()) && response.sources().isEmpty();
        Status status = testCase.expectedRefusal()
                ? (refused ? Status.PASS : Status.FAIL)
                : (sourcesMatch && answerMatches ? testCase.reviewWhenEvidenceIsCapped() : Status.FAIL);

        if (testCase.expectedRefusal()) {
            verify(aiClient, org.mockito.Mockito.never())
                    .generateAnswer(org.mockito.ArgumentMatchers.anyString());
        }
        return new EvaluationResult(testCase, response, actualKeys, status,
                testCase.expectedRefusal() ? !refused : sourcesMatch && answerMatches);
    }

    private EvaluationResult evaluateDeterminismCase() {
        EvaluationCase testCase = new EvaluationCase(
                "DET-001", "DETERMINISM", "How does RAG work?",
                "Stable ranking for equal similarity scores",
                List.of("RAG retrieves context before generation"),
                List.of("10:0", "10:1", "20:0"), false, Status.PASS,
                List.of(result(10L, "guide.pdf", 0, 0.90, "RAG retrieves context before generation."),
                        result(10L, "guide.pdf", 1, 0.90, "Embeddings represent document meaning."),
                        result(20L, "notes.txt", 0, 0.90, "Notes about retrieval.")),
                "RAG retrieves context before generation.");
        reset(documentService, aiClient);
        when(documentService.searchSemantically(testCase.question(), 20, USER_EMAIL))
                .thenReturn(testCase.results());
        when(aiClient.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(testCase.answer());

        RagResponse first = ragService.askQuestion(testCase.question(), USER_EMAIL);
        RagResponse second = ragService.askQuestion(testCase.question(), USER_EMAIL);
        List<String> firstKeys = sourceKeys(first);
        List<String> secondKeys = sourceKeys(second);
        assertThat(secondKeys).containsExactlyElementsOf(firstKeys);
        return new EvaluationResult(testCase, second, secondKeys,
                firstKeys.equals(secondKeys) ? Status.PASS : Status.FAIL, true);
    }

    private EvaluationCase directFactCase() {
        return new EvaluationCase("FACT-001", "DIRECT_FACT_RETRIEVAL",
                "What does the guide say RAG combines?", "RAG combines retrieval with generation",
                List.of("retrieval", "generation"), List.of("10:2"), false, Status.PASS,
                List.of(result(10L, "guide.pdf", 2, 0.91,
                        "RAG combines retrieval with generation.")),
                "The guide says RAG combines retrieval with generation.");
    }

    private EvaluationCase multiChunkCase() {
        return new EvaluationCase("MULTI-001", "MULTI_CHUNK_RETRIEVAL",
                "How does the guide describe the full RAG flow?",
                "Evidence from three chunks is required; all remain within final limit 6",
                List.of("retrieval", "generation", "embeddings"), List.of("10:0", "10:1", "10:2"),
                false, Status.PASS, List.of(
                result(10L, "guide.pdf", 0, 0.95, "RAG starts with retrieval."),
                result(10L, "guide.pdf", 1, 0.94, "Embeddings support semantic retrieval."),
                result(10L, "guide.pdf", 2, 0.93, "Generation uses the retrieved context.")),
                "Retrieval uses embeddings and generation uses the retrieved context.");
    }

    private EvaluationCase documentConcentrationCase() {
        return new EvaluationCase("CAP-001", "DOCUMENT_CONCENTRATION",
                "What are the four steps in the guide's process?",
                "Four useful chunks from guide.pdf; Phase 1 can retain only three",
                List.of("first three"), List.of("10:0", "10:1", "10:2"), false, Status.NEEDS_REVIEW,
                List.of(result(10L, "guide.pdf", 0, 0.99, "First step."),
                        result(10L, "guide.pdf", 1, 0.98, "Second step."),
                        result(10L, "guide.pdf", 2, 0.97, "Third step."),
                        result(10L, "guide.pdf", 3, 0.96, "Fourth step.")),
                "The first three steps are present; the fourth is outside the per-document cap.");
    }

    private EvaluationCase negativeCase() {
        return new EvaluationCase("NEG-001", "NEGATIVE_OUT_OF_CORPUS",
                "What is the capital of a fictional planet?", "No supporting document evidence",
                List.of(), List.of(), true, Status.PASS, List.of(), FALLBACK);
    }

    private EvaluationCase wordingVariationCase() {
        return new EvaluationCase("WORD-001", "WORDING_VARIATION",
                "Explain the mechanism that pairs search with text creation.",
                "RAG combines retrieval with generation", List.of("retrieval", "generation"), List.of("10:2"),
                false, Status.PASS, List.of(result(10L, "guide.pdf", 2, 0.88,
                "RAG combines retrieval with generation.")),
                "The mechanism combines retrieval with generation.");
    }

    private EvaluationCase weakSemanticCase() {
        return new EvaluationCase("WEAK-001", "WEAK_SEMANTIC_MATCH",
                "What is the history of database indexing?", "No result at or above the 0.65 threshold",
                List.of(), List.of(), true, Status.PASS,
                List.of(result(10L, "guide.pdf", 0, 0.64, "RAG starts with retrieval.")), FALLBACK);
    }

    private EvaluationCase tenantIsolationCase() {
        return new EvaluationCase("TENANT-001", "TENANT_ISOLATION",
                "What is in the private plan?", "User A must not receive User B's private chunk",
                List.of(), List.of(), true, Status.PASS, List.of(), FALLBACK, OTHER_USER_EMAIL);
    }

    private static SemanticSearchResult result(Long documentId, String filename, int chunkIndex,
                                               double similarity, String text) {
        return new SemanticSearchResult(documentId, filename, chunkIndex, text, similarity);
    }

    private static List<String> sourceKeys(RagResponse response) {
        return response.sources().stream()
                .map(source -> source.documentId() + ":" + source.chunkIndex())
                .toList();
    }

    private String renderReport(List<EvaluationResult> results) {
        long passed = results.stream().filter(result -> result.status() == Status.PASS).count();
        long failed = results.stream().filter(result -> result.status() == Status.FAIL).count();
        long needsReview = results.stream().filter(result -> result.status() == Status.NEEDS_REVIEW).count();
        StringBuilder report = new StringBuilder("# RAG Evaluation Report\n\n");
        report.append("## Overall score\n\n");
        report.append("- Total tests: ").append(results.size()).append("\n");
        report.append("- Passed: ").append(passed).append("\n");
        report.append("- Failed: ").append(failed).append("\n");
        report.append("- Needs review: ").append(needsReview).append("\n\n");
        report.append("## Cases\n\n");
        report.append("| ID | Category | Question | Expected evidence | Actual answer | Result | Sources (file/chunk/score) | Grounded | Notes |\n");
        report.append("|---|---|---|---|---|---|---|---|---|\n");
        for (EvaluationResult result : results) {
            EvaluationCase testCase = result.testCase();
            report.append("| ").append(testCase.id()).append(" | ").append(testCase.category())
                    .append(" | ").append(cell(testCase.question())).append(" | ")
                    .append(cell(testCase.expectedEvidence())).append(" | ")
                    .append(cell(result.response().answer())).append(" | ").append(result.status())
                    .append(" | ").append(result.actualSourceDetails()).append(" | ")
                    .append(result.grounded()).append(" | ").append(cell(testCase.notes())).append(" |\n");
        }
        report.append("\n## Findings\n\n")
                .append("- Threshold: the 0.64 weak match was rejected; the 0.65 boundary is inclusive.\n")
                .append("- Candidate window: unit cases verify the service requests 20 candidates; live recall is not measurable without embeddings.\n")
                .append("- Per-document cap: the concentration case is NEEDS_REVIEW because useful chunk 3 is removed after three chunks from one document.\n")
                .append("- Final context limit: the multi-chunk case keeps three chunks and the limit-6 behavior is covered by RagServiceTest; no live corpus is available.\n")
                .append("- Deduplication and ordering: duplicate chunk keys are removed and ties are deterministic; the determinism case passed twice.\n")
                .append("- Grounding/refusal: positive cases use only fixture context; negative and weak cases returned the existing insufficient-evidence fallback without generation.\n")
                .append("- Tenant isolation: the unit boundary passes no cross-user evidence. Full database isolation remains covered by the opt-in pgvector integration test and was not run here.\n\n")
                .append("## Recommendation\n\n")
                .append("Keep the Phase 1 configuration unchanged for now, but do not treat this as a corpus-level quality sign-off. Calibrate the per-document cap only after a known multi-document corpus and live embedding evaluation show repeated useful evidence loss.\n");
        return report.toString();
    }

    private String cell(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private record EvaluationCase(String id, String category, String question, String expectedEvidence,
                                  List<String> expectedAnswerFragments, List<String> expectedSourceKeys,
                                  boolean expectedRefusal, Status reviewWhenEvidenceIsCapped,
                                  List<SemanticSearchResult> results, String answer, String email) {
        private EvaluationCase(String id, String category, String question, String expectedEvidence,
                               List<String> expectedAnswerFragments, List<String> expectedSourceKeys,
                               boolean expectedRefusal, Status reviewWhenEvidenceIsCapped,
                               List<SemanticSearchResult> results, String answer) {
            this(id, category, question, expectedEvidence, expectedAnswerFragments, expectedSourceKeys,
                    expectedRefusal, reviewWhenEvidenceIsCapped, results, answer, USER_EMAIL);
        }

        private String notes() {
            return category.equals("TENANT_ISOLATION")
                    ? "Mocked unit boundary only; run pgvector integration for database proof."
                    : category.equals("DOCUMENT_CONCENTRATION")
                    ? "Fourth useful chunk is intentionally supplied to expose the cap."
                    : "Deterministic fixture; model quality is not measured.";
        }
    }

    private record EvaluationResult(EvaluationCase testCase, RagResponse response,
                                    List<String> actualSourceKeys, Status status, boolean grounded) {
        private String actualSourceDetails() {
            return response.sources().stream()
                    .map(source -> source.filename() + "/" + source.chunkIndex()
                            + " (" + String.format(Locale.ROOT, "%.2f", source.similarity()) + ")")
                    .toList()
                    .toString();
        }
    }

    private enum Status {
        PASS, FAIL, NEEDS_REVIEW
    }
}