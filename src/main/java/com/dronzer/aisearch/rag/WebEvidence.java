package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.Objects;

/**
 * Evidence retrieved from an external web search provider.
 *
 * <p>This is a contract only for Phase 2B-0. It wraps the existing
 * {@link com.dronzer.aisearch.dto.WebSearchResult} shape so that web
 * evidence can flow through the new provenance model with its URL, title,
 * publisher, and snippet preserved.</p>
 */
public record WebEvidence(

        /**
         * The URL of the web result.
         */
        String url,

        /**
         * The title of the web result.
         */
        String title,

        /**
         * The publisher or hostname of the source.
         */
        String publisher,

        /**
         * The snippet or excerpt from the web result.
         */
        String content,

        /**
         * A provider-specific relevance score, if one is available.
         */
        Double retrievalScore,

        /**
         * Retrieval metadata such as the search provider name or query
         * context. May be null.
         */
        String retrievalMethod

) implements Evidence {

    @Override
    public KnowledgeSource source() {
        return KnowledgeSource.WEB;
    }

    @Override
    public String id() {
        return url;
    }

    @Override
    public String provenance() {
        return title + ", " + publisher;
    }

    @Override
    public Double score() {
        return retrievalScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebEvidence that)) return false;
        return Objects.equals(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id());
    }

    public static WebEvidence fromUrlAndSnippet(String url, String title, String publisher, String content) {
        return new WebEvidence(url, title, publisher, content, null, null);
    }
}
