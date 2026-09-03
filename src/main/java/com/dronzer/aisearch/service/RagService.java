package com.dronzer.aisearch.service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.RagSource;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final int RETRIEVAL_LIMIT = 5;
    private static final String NO_RELEVANT_INFORMATION_ANSWER =
            "I could not find relevant information in your documents.";

    private final DocumentService documentService;
    private final AIClient aiClient;

    public RagService(DocumentService documentService, AIClient aiClient) {
        this.documentService = documentService;
        this.aiClient = aiClient;
    }

    public RagResponse askQuestion(String question, String email) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }

        List<SemanticSearchResult> results = documentService.searchSemantically(
                question,
                RETRIEVAL_LIMIT,
                email);

        if (results.isEmpty()) {
            return new RagResponse(NO_RELEVANT_INFORMATION_ANSWER, List.of());
        }

        String prompt = buildPrompt(question, buildContext(results));
        String answer = aiClient.generateAnswer(prompt);

        List<RagSource> sources = results.stream()
                .map(this::toSource)
                .toList();

        return new RagResponse(answer, sources);
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
}
