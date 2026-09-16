package com.dronzer.aisearch.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.RagSource;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.dto.WebSearchResult;

@Service
public class RagService {

    private static final String NO_RELEVANT_INFORMATION_ANSWER =
            "I could not find relevant information in your documents.";
    private static final int MAX_WEB_RESULTS = 20;
    private static final int MAX_WEB_SNIPPET_LENGTH = 2000;

    private final DocumentService documentService;
    private final AIClient aiClient;
    private final WebSearchClient webSearchClient;

    @Value("${app.rag.web-fallback-enabled:false}")
    private boolean webFallbackEnabled;

    @Value("${app.rag.web-result-limit:5}")
    private int webResultLimit;

    @Value("${app.rag.retrieval-candidate-limit:20}")
    private int retrievalCandidateLimit = 20;

    @Value("${app.rag.final-context-limit:6}")
    private int finalContextLimit = 6;

    @Value("${app.rag.similarity-threshold:0.65}")
    private double similarityThreshold = 0.65;

    @Value("${app.rag.max-chunks-per-document:3}")
    private int maxChunksPerDocument = 3;

    @Autowired
    public RagService(
            DocumentService documentService,
            AIClient aiClient,
            WebSearchClient webSearchClient) {
        this.documentService = documentService;
        this.aiClient = aiClient;
        this.webSearchClient = webSearchClient;
    }

    public RagService(DocumentService documentService, AIClient aiClient) {
        this(documentService, aiClient, (query, limit) -> List.of());
    }

    public RagResponse askQuestion(String question, String email) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }

        List<SemanticSearchResult> results = documentService.searchSemantically(
                question,
                retrievalCandidateLimit,
                email);

        List<SemanticSearchResult> evidence = selectEvidence(results);
        if (evidence.isEmpty()) {
            if (!webFallbackEnabled) {
                return new RagResponse(
                        NO_RELEVANT_INFORMATION_ANSWER,
                        List.of(),
                        List.of(),
                        RagOrigin.INSUFFICIENT_EVIDENCE);
            }

            int boundedWebResultLimit = Math.min(Math.max(webResultLimit, 1), MAX_WEB_RESULTS);
            List<WebSearchResult> webResults = webSearchClient.search(question, boundedWebResultLimit);
            if (webResults.isEmpty()) {
                return new RagResponse(
                        NO_RELEVANT_INFORMATION_ANSWER,
                        List.of(),
                        List.of(),
                        RagOrigin.INSUFFICIENT_EVIDENCE);
            }

            String answer = aiClient.generateAnswer(buildWebPrompt(question, webResults));
            return new RagResponse(answer, List.of(), webResults, RagOrigin.WEB);
        }

        String prompt = buildPrompt(question, buildContext(evidence));
        String answer = aiClient.generateAnswer(prompt);

        List<RagSource> sources = evidence.stream()
                .map(this::toSource)
                .toList();

        return new RagResponse(answer, sources, List.of(), RagOrigin.DOCUMENTS);
    }

    private List<SemanticSearchResult> selectEvidence(List<SemanticSearchResult> results) {
        Comparator<SemanticSearchResult> ranking = Comparator
                .comparingDouble(SemanticSearchResult::similarity)
                .reversed()
                .thenComparing(SemanticSearchResult::documentId)
                .thenComparing(SemanticSearchResult::chunkIndex);

        Map<ChunkKey, SemanticSearchResult> uniqueResults = new LinkedHashMap<>();
        results.stream()
                .filter(result -> result.similarity() >= similarityThreshold)
                .sorted(ranking)
                .forEach(result -> uniqueResults.putIfAbsent(
                        new ChunkKey(result.documentId(), result.chunkIndex()), result));

        Map<Long, Integer> chunksPerDocument = new LinkedHashMap<>();
        List<SemanticSearchResult> evidence = uniqueResults.values().stream()
                .filter(result -> chunksPerDocument.merge(
                        result.documentId(), 1, Integer::sum) <= maxChunksPerDocument)
                .limit(finalContextLimit)
                .toList();

        return evidence;
    }

    private String buildContext(List<SemanticSearchResult> results) {
        StringBuilder context = new StringBuilder();

        for (int index = 0; index < results.size(); index++) {
            SemanticSearchResult result = results.get(index);
            context.append("[Source ")
                    .append(index + 1)
                    .append(": ")
                    .append(result.filename())
                    .append(", chunk ")
                    .append(result.chunkIndex())
                    .append("]\n\n")
                    .append(result.chunkText())
                    .append("\n\n");
        }

        return context.toString();
    }

    private String buildPrompt(String question, String context) {
        return """
                You are a document question-answering assistant.

                Answer the user's question using ONLY the provided document context.

                Rules:
                - Do not use outside knowledge.
                - Do not invent information.
                - If the answer is not contained in the context, clearly say that you could not find the answer in the uploaded documents.
                - Give a concise and accurate answer.

                DOCUMENT CONTEXT:
                %s
                USER QUESTION:
                %s
                """.formatted(context, question);
    }

    private RagSource toSource(SemanticSearchResult result) {
        return new RagSource(
                result.documentId(),
                result.filename(),
                result.chunkIndex(),
                result.similarity());
    }

    private record ChunkKey(Long documentId, Integer chunkIndex) {
    }

    private String buildWebPrompt(String question, List<WebSearchResult> results) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            WebSearchResult result = results.get(index);
            context.append("[Web source ")
                    .append(index + 1)
                    .append(": ")
                    .append(result.title())
                    .append(", ")
                    .append(result.url())
                    .append("]\n")
                    .append(boundedSnippet(result.snippet()))
                    .append("\n\n");
        }

        return """
                You are a web-grounded question-answering assistant.

                Answer the user's question using ONLY the supplied web search evidence.

                Rules:
                - Do not use outside knowledge.
                - Do not invent information or sources.
                - If the evidence is insufficient, clearly say that you could not find a supported answer.
                - Give a concise and accurate answer.

                WEB SEARCH EVIDENCE:
                %s
                USER QUESTION:
                %s
                """.formatted(context, question);
    }

    private String boundedSnippet(String snippet) {
        if (snippet == null) {
            return "";
        }
        return snippet.length() <= MAX_WEB_SNIPPET_LENGTH
                ? snippet
                : snippet.substring(0, MAX_WEB_SNIPPET_LENGTH);
    }
}
