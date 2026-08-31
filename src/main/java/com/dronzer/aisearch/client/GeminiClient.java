package com.dronzer.aisearch.client;

import com.dronzer.aisearch.dto.gemini.Content;
import com.dronzer.aisearch.dto.gemini.EmbeddingRequest;
import com.dronzer.aisearch.dto.gemini.Part;
import com.dronzer.aisearch.model.EmbeddingVector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient implements AIClient {

    private static final int EMBEDDING_DIMENSIONS = 768;

    private final RestTemplate restTemplate;

    private final String apiKey;

    private final String embeddingUrl;

    private final String chatUrl;

    public GeminiClient(
            RestTemplate restTemplate,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.embedding.url}") String embeddingUrl,
            @Value("${gemini.chat.url}") String chatUrl) {

        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.embeddingUrl = embeddingUrl;
        this.chatUrl = chatUrl;
    }

    @Override
    public EmbeddingVector generateDocumentEmbedding(String text) {
        return generateEmbedding(text, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public EmbeddingVector generateQueryEmbedding(String text) {
        return generateEmbedding(text, "RETRIEVAL_QUERY");
    }

    @Override
    public String generateAnswer(String prompt) {
        requireText(prompt, "prompt");

        Map<String, Object> request = Map.of(
                "contents", List.of(new Content(List.of(new Part(prompt)))));

        JsonNode body = postForBody(chatUrl, request);
        JsonNode parts = body.path("candidates").path(0).path("content").path("parts");

        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini returned no answer candidates");
        }

        StringBuilder answer = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode text = part.path("text");
            if (text.isTextual()) {
                answer.append(text.asText());
            }
        }

        if (answer.isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty answer");
        }

        return answer.toString();
    }

    private JsonNode postForBody(String url, Object request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                JsonNode.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Gemini returned an empty or unsuccessful response");
        }

        return response.getBody();
    }

    private EmbeddingVector generateEmbedding(String text, String taskType) {
        requireText(text, "text");

        EmbeddingRequest request = new EmbeddingRequest(
                taskType,
                new Content(List.of(new Part(text))),
                EMBEDDING_DIMENSIONS);

        JsonNode body = postForBody(embeddingUrl, request);
        JsonNode values = body.path("embedding").path("values");

        if (!values.isArray() || values.size() != EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Gemini returned an embedding with an unexpected dimension");
        }

        List<Float> vector = new ArrayList<>(EMBEDDING_DIMENSIONS);
        double squaredMagnitude = 0;

        for (JsonNode value : values) {
            if (!value.isNumber()) {
                throw new IllegalStateException("Gemini returned a non-numeric embedding value");
            }

            float component = value.floatValue();
            vector.add(component);
            squaredMagnitude += component * component;
        }

        if (squaredMagnitude == 0) {
            throw new IllegalStateException("Gemini returned a zero-magnitude embedding");
        }

        double magnitude = Math.sqrt(squaredMagnitude);
        for (int index = 0; index < vector.size(); index++) {
            vector.set(index, (float) (vector.get(index) / magnitude));
        }

        return new EmbeddingVector(vector);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
