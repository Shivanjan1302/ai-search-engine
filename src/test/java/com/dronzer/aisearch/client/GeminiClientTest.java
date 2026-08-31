package com.dronzer.aisearch.client;

import com.dronzer.aisearch.dto.gemini.EmbeddingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesAnEmbeddingFromGeminiResponse() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://example.test/embed"),
                any(),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(
                        embeddingResponse(),
                        HttpStatus.OK));

        GeminiClient client = new GeminiClient(
                restTemplate, "test-key", "https://example.test/embed", "https://example.test/chat");

        assertThat(client.generateDocumentEmbedding("A document chunk").size())
                .isEqualTo(768);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://example.test/embed"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode.class));

        EmbeddingRequest request = (EmbeddingRequest) requestCaptor.getValue().getBody();
        assertThat(request.getTaskType()).isEqualTo("RETRIEVAL_DOCUMENT");
        assertThat(request.getOutputDimensionality()).isEqualTo(768);
    }

    @Test
    void generatesAQueryEmbeddingWithTheRetrievalQueryTask() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://example.test/embed"),
                any(),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(embeddingResponse(), HttpStatus.OK));

        GeminiClient client = new GeminiClient(
                restTemplate, "test-key", "https://example.test/embed", "https://example.test/chat");

        client.generateQueryEmbedding("Where is the project plan?");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://example.test/embed"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode.class));

        EmbeddingRequest request = (EmbeddingRequest) requestCaptor.getValue().getBody();
        assertThat(request.getTaskType()).isEqualTo("RETRIEVAL_QUERY");
    }

    @Test
    void generatesAnAnswerFromGeminiResponse() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://example.test/chat"),
                any(),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(
                        objectMapper.readTree("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello \"},{\"text\":\"there\"}]}}]}"),
                        HttpStatus.OK));

        GeminiClient client = new GeminiClient(
                restTemplate, "test-key", "https://example.test/embed", "https://example.test/chat");

        assertThat(client.generateAnswer("Say hello")).isEqualTo("Hello there");
    }

    private ObjectNode embeddingResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode values = response.putObject("embedding").putArray("values");
        values.add(1.0f);
        for (int index = 1; index < 768; index++) {
            values.add(0.0f);
        }
        return response;
    }
}
