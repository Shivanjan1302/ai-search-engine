package com.dronzer.aisearch.client;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestTemplate;

import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.exception.WebSearchUpstreamException;

class TavilyWebSearchClientTest {

    @Test
    void mapsTavilyResultsAndBoundsSnippetContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String longContent = "x".repeat(2500);
        server.expect(requestTo("https://api.tavily.com/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.query").value("test query"))
                .andExpect(jsonPath("$.max_results").value(5))
                .andRespond(withSuccess("""
                        {"results":[
                          {"title":"Example", "url":"https://example.com/page", "content":"%s"},
                          {"title":"Unsafe", "url":"javascript:alert(1)", "content":"ignore"}
                        ]}
                        """.formatted(longContent), org.springframework.http.MediaType.APPLICATION_JSON));

        List<WebSearchResult> results = new TavilyWebSearchClient(restTemplate, "test-key")
                .search("test query", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Example");
        assertThat(results.get(0).publisher()).isEqualTo("example.com");
        assertThat(results.get(0).snippet()).hasSize(2000);
        server.verify();
    }

    @Test
    void missingKeyFailsWithoutMakingARequest() {
        RestTemplate restTemplate = new RestTemplate();

        assertThatThrownBy(() -> new TavilyWebSearchClient(restTemplate, " ").search("query", 5))
                .isInstanceOf(WebSearchUpstreamException.class)
                .hasMessage("Web search is temporarily unavailable");
    }
}