package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.WebSearchResult;
import java.util.List;
import java.util.Objects;

/**
 * Convert a list of {@link WebSearchResult} into {@link WebEvidence}.
 *
 * <p>This adapter exists so that web search results can flow into the new
 * evidence model without losing URL, title, publisher, or snippet. It performs
 * no new retrieval logic and no new HTTP calls.</p>
 *
 * <p>The adapter is stateless and thread-safe.</p>
 */
public final class WebRetrievalAdapter {

    private WebRetrievalAdapter() {
        // utility class
    }

    /**
     * Convert a list of {@link WebSearchResult} into {@link WebEvidence}.
     *
     * @param results the web search results to convert, never null
     * @return the corresponding web evidence, in the same order
     */
    public static List<WebEvidence> fromResults(List<WebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .map(WebRetrievalAdapter::fromResult)
                .toList();
    }

    /**
     * Convert a single {@link WebSearchResult} into {@link WebEvidence}.
     *
     * @param result the web search result to convert, never null
     * @return the corresponding web evidence
     */
    public static WebEvidence fromResult(WebSearchResult result) {
        Objects.requireNonNull(result, "WebSearchResult must not be null");
        return new WebEvidence(
                result.url(),
                result.title(),
                result.publisher(),
                result.snippet(),
                null,
                "web",
                result.domain(),
                result.path(),
                result.breadcrumb(),
                result.publishedDate()
        );
    }
}
