package com.dronzer.aisearch.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.dronzer.aisearch.dto.WebSearchResult;
import com.dronzer.aisearch.exception.WebSearchUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class TavilyWebSearchClient implements WebSearchClient {

    private static final String SEARCH_URL = "https://api.tavily.com/search";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_SNIPPET_LENGTH = 2000;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public TavilyWebSearchClient(
            RestTemplate restTemplate,
            @Value("${TAVILY_API_KEY:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Override
    public List<WebSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new WebSearchUpstreamException();
        }

        int boundedLimit = Math.min(Math.max(limit, 1), MAX_RESULTS);
        Map<String, Object> request = Map.of(
                "query", query,
                "search_depth", "basic",
                "topic", "general",
                "max_results", boundedLimit,
                "include_answer", false,
                "include_raw_content", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    SEARCH_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new WebSearchUpstreamException();
            }
            return mapResults(response.getBody());
        } catch (RestClientException | WebSearchUpstreamException exception) {
            throw new WebSearchUpstreamException();
        }
    }

    private List<WebSearchResult> mapResults(JsonNode body) {
        JsonNode results = body.path("results");
        if (!results.isArray()) {
            throw new WebSearchUpstreamException();
        }

        List<WebSearchResult> mapped = new ArrayList<>();
        for (JsonNode result : results) {
            String title = text(result, "title");
            String url = text(result, "url");
            String content = text(result, "content");
            String publishedDate = text(result, "published_date");
            if (title.isBlank() || url.isBlank() || content.isBlank() || !isHttpUrl(url)) {
                continue;
            }
            String snippet = content.length() <= MAX_SNIPPET_LENGTH
                    ? content
                    : content.substring(0, MAX_SNIPPET_LENGTH);
            mapped.add(new WebSearchResult(title, url, snippet, publisher(url), null, null, null, publishedDate));
        }
        return List.copyOf(mapped);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private boolean isHttpUrl(String value) {
        try {
            String scheme = URI.create(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String publisher(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}