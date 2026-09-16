package com.dronzer.aisearch.integration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.client.TavilyWebSearchClient;
import com.dronzer.aisearch.dto.RagOrigin;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.service.DocumentService;
import com.dronzer.aisearch.service.RagService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIf("liveTavilyTestsEnabled")
class TavilyWebSearchLiveIntegrationTest {

    private static final String QUERY = "Java 25 programming language";
    private static final int RESULT_LIMIT = 5;
    private static final int MAX_SNIPPET_LENGTH = 2000;

    @Test
    void mapsResultsFromRealTavilyResponse() {
        List<WebSearchResult> results = tavilyClient().search(QUERY, RESULT_LIMIT);

        assertThat(results).isNotNull().isNotEmpty();
        assertThat(results).allSatisfy(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).matches("https?://.+");
            assertThat(result.snippet()).isNotNull().hasSizeLessThanOrEqualTo(MAX_SNIPPET_LENGTH);
        });
    }

    @Test
    void realTavilyResultsTriggerWebFallback() {
        DocumentService documentService = mock(DocumentService.class);
        AIClient aiClient = mock(AIClient.class);
        TavilyWebSearchClient tavilyClient = tavilyClient();
        RagService ragService = new RagService(documentService, aiClient, tavilyClient);

        when(documentService.searchSemantically(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString()))
                .thenReturn(List.of());
        when(aiClient.generateAnswer(anyString())).thenReturn("Web-grounded test answer.");
        ReflectionTestUtils.setField(ragService, "webFallbackEnabled", true);
        ReflectionTestUtils.setField(ragService, "webResultLimit", RESULT_LIMIT);

        RagResponse response = ragService.askQuestion(QUERY, "live-test@example.test");

        assertThat(response.origin()).isEqualTo(RagOrigin.WEB);
        assertThat(response.sources()).isEmpty();
        assertThat(response.webSources()).isNotEmpty();
        assertThat(response.webSources()).allSatisfy(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).matches("https?://.+");
            assertThat(result.snippet()).isNotNull().hasSizeLessThanOrEqualTo(MAX_SNIPPET_LENGTH);
        });
    }

    private TavilyWebSearchClient tavilyClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(15_000);
        return new TavilyWebSearchClient(new RestTemplate(requestFactory), System.getenv("TAVILY_API_KEY"));
    }

    @SuppressWarnings("unused")
    static boolean liveTavilyTestsEnabled() {
        return "true".equalsIgnoreCase(System.getenv("RUN_TAVILY_INTEGRATION_TESTS"))
                && System.getenv("TAVILY_API_KEY") != null
                && !System.getenv("TAVILY_API_KEY").isBlank();
    }
}