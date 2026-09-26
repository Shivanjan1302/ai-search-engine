package com.dronzer.aisearch.client;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.dronzer.aisearch.dto.gemini.EmbeddingRequest;
import com.dronzer.aisearch.exception.GeminiUpstreamException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    @Test
    void translatesGeminiTimeoutIntoStableApplicationException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://example.test/embed"),
                any(),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new ResourceAccessException("connection timed out"));

        GeminiClient client = new GeminiClient(
                restTemplate, "test-key", "https://example.test/embed", "https://example.test/chat");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> client.generateDocumentEmbedding("A document chunk")))
                .isInstanceOf(com.dronzer.aisearch.exception.GeminiUpstreamException.class)
                .hasMessage("Gemini service is temporarily unavailable");
    }

    @Test
    void preservesUpstreamStatusWhenGeminiRespondsWithAnError() {
        assertThat(upstreamStatusForFailingCall(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(429);
        assertThat(upstreamStatusForFailingCall(HttpStatus.BAD_REQUEST)).isEqualTo(400);
        assertThat(upstreamStatusForFailingCall(HttpStatus.FORBIDDEN)).isEqualTo(403);
        assertThat(upstreamStatusForFailingCall(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo(500);
    }

    @Test
    void mapsUpstreamStatusToStableSafeCodesWithoutProviderDetail() {
        GeminiUpstreamException quota = new GeminiUpstreamException(429);
        assertThat(quota.code()).isEqualTo(GeminiUpstreamException.QUOTA_CODE);
        assertThat(quota.applicationStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(quota.getMessage()).isEqualTo("Gemini request quota is currently exhausted");

        GeminiUpstreamException badRequest = new GeminiUpstreamException(400);
        assertThat(badRequest.code()).isEqualTo(GeminiUpstreamException.BAD_REQUEST_CODE);
        assertThat(badRequest.applicationStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        GeminiUpstreamException forbidden = new GeminiUpstreamException(403);
        assertThat(forbidden.code()).isEqualTo(GeminiUpstreamException.FORBIDDEN_CODE);
        assertThat(forbidden.applicationStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        GeminiUpstreamException other = new GeminiUpstreamException(503);
        assertThat(other.code()).isEqualTo(GeminiUpstreamException.CODE);
        assertThat(other.applicationStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void keepsUnexpectedTransportFailuresSafelyMappedWithoutStatus() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://example.test/chat"),
                any(),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new ResourceAccessException("connection timed out"));

        GeminiClient client = new GeminiClient(
                restTemplate, "test-key", "https://example.test/embed", "https://example.test/chat");

        GeminiUpstreamException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client.generateAnswer("Say hello"), GeminiUpstreamException.class);

        assertThat(failure.upstreamStatus()).isNull();
        assertThat(failure.code()).isEqualTo(GeminiUpstreamException.CODE);
        assertThat(failure.applicationStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(failure.getMessage()).isEqualTo("Gemini service is temporarily unavailable");
    }

    @Test
    void neverCarriesTheApiKeyOrProviderBodyIntoTheException() {
        GeminiUpstreamException failure = new GeminiUpstreamException(429);

        assertThat(failure.getMessage())
                .doesNotContain("test-key")
                .doesNotContain("AIza")
                .doesNotContain("RESOURCE_EXHAUSTED")
                .doesNotContain("x-goog-api-key");
        assertThat(failure.getCause()).isNull();
    }

    private int upstreamStatusForFailingCall(HttpStatus status) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("https://example.test/chat"),
                any(),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new HttpServerErrorException(status));

        GeminiClient client = new GeminiClient(
                restTemplate, "test-key", "https://example.test/embed", "https://example.test/chat");

        GeminiUpstreamException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client.generateAnswer("Say hello"), GeminiUpstreamException.class);

        return failure.upstreamStatus();
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
