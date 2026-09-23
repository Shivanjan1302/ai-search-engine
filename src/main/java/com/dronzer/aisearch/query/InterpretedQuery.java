package com.dronzer.aisearch.query;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * A future-proof representation of an interpreted user query.
 *
 * <p>This is a contract only for Phase 2B-0. No classification logic is implemented here.
 * The intent is to give later phases a stable shape to fill in without another interface
 * rewrite.</p>
 *
 * <p>The taxonomy of {@link QueryIntent} is deliberately open-ended: it is expected that
 * later phases will expand or replace it. For 2B-0 it is enough that the shape exists.</p>
 */
public record InterpretedQuery(

        /** The original user question, exactly as received. */
        String originalQuery,

        /** An optional normalized/standalone form produced by a future query rewriter. */
        Optional<String> normalizedQuery,

        /** A future classification of what the query is asking for. */
        Optional<QueryIntent> intent,

        /**
         * Whether the query appears to reference the user's own documents
         * (for example: "our leave policy", "in my notes").
         * Absent means the classifier has not yet determined this.
         * Must not be treated as {@code false} by downstream consumers.
         */
        Optional<Boolean> documentSpecific,

        /**
         * Whether the query requires current/fresh external information.
         * Absent means the classifier has not yet determined this.
         * Must not be treated as {@code false} by downstream consumers.
         */
        Optional<Boolean> requiresFreshness,

        /** The freshness horizon, if {@link #requiresFreshness} is present and true. */
        Optional<ChronoUnit> freshnessHorizon,

        /** Whether web/current information is required to answer well. */
        Optional<Boolean> webRequired,

        /** Whether model parametric knowledge is permitted as a supplementary source. */
        Optional<Boolean> modelKnowledgeAllowed,

        /** Whether the query is expected to need more than one source type. */
        Optional<Boolean> mixedSource,

        /** Optional conversation context for future multi-turn handling. */
        Optional<ConversationContext> conversationContext

) {

    public InterpretedQuery(String originalQuery) {
        this(originalQuery, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Build a copy with an adjusted {@link QueryIntent}.
     */
    public InterpretedQuery withIntent(QueryIntent intent) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                Optional.of(intent),
                documentSpecific,
                requiresFreshness,
                freshnessHorizon,
                webRequired,
                modelKnowledgeAllowed,
                mixedSource,
                conversationContext);
    }

    /**
     * Build a copy with an adjusted normalized query.
     */
    public InterpretedQuery withNormalizedQuery(String normalized) {
        return new InterpretedQuery(
                originalQuery,
                Optional.of(normalized),
                intent,
                documentSpecific,
                requiresFreshness,
                freshnessHorizon,
                webRequired,
                modelKnowledgeAllowed,
                mixedSource,
                conversationContext);
    }

    /**
     * Build a copy with conversation context attached.
     */
    public InterpretedQuery withConversationContext(ConversationContext context) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                intent,
                documentSpecific,
                requiresFreshness,
                freshnessHorizon,
                webRequired,
                modelKnowledgeAllowed,
                mixedSource,
                Optional.of(context));
    }

    /**
     * Build a copy with an explicit document-specific signal.
     * Use this when a classifier produces a definitive signal,
     * or when an authoritative component overrides the classifier.
     */
    public InterpretedQuery withDocumentSpecific(boolean documentSpecific) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                intent,
                Optional.of(documentSpecific),
                requiresFreshness,
                freshnessHorizon,
                webRequired,
                modelKnowledgeAllowed,
                mixedSource,
                conversationContext);
    }

    /**
     * Build a copy with an explicit freshness requirement signal.
     */
    public InterpretedQuery withRequiresFreshness(boolean requiresFreshness) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                intent,
                documentSpecific,
                Optional.of(requiresFreshness),
                freshnessHorizon,
                webRequired,
                modelKnowledgeAllowed,
                mixedSource,
                conversationContext);
    }

    /**
     * Build a copy with an explicit web-required signal.
     */
    public InterpretedQuery withWebRequired(boolean webRequired) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                intent,
                documentSpecific,
                requiresFreshness,
                freshnessHorizon,
                Optional.of(webRequired),
                modelKnowledgeAllowed,
                mixedSource,
                conversationContext);
    }

    /**
     * Build a copy with an explicit model-knowledge-allowed signal.
     */
    public InterpretedQuery withModelKnowledgeAllowed(boolean modelKnowledgeAllowed) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                intent,
                documentSpecific,
                requiresFreshness,
                freshnessHorizon,
                webRequired,
                Optional.of(modelKnowledgeAllowed),
                mixedSource,
                conversationContext);
    }

    /**
     * Build a copy with an explicit mixed-source signal.
     */
    public InterpretedQuery withMixedSource(boolean mixedSource) {
        return new InterpretedQuery(
                originalQuery,
                normalizedQuery,
                intent,
                documentSpecific,
                requiresFreshness,
                freshnessHorizon,
                webRequired,
                modelKnowledgeAllowed,
                Optional.of(mixedSource),
                conversationContext);
    }

    /**
     * Returns whether the classifier has determined a document-specific signal.
     */
    public boolean hasDocumentSpecific() {
        return documentSpecific.isPresent();
    }

    /**
     * Returns whether the classifier has determined a freshness requirement signal.
     */
    public boolean hasRequiresFreshness() {
        return requiresFreshness.isPresent();
    }

    /**
     * Returns whether the classifier has determined a web-required signal.
     */
    public boolean hasWebRequired() {
        return webRequired.isPresent();
    }

    /**
     * Returns whether the classifier has determined a model-knowledge-allowed signal.
     */
    public boolean hasModelKnowledgeAllowed() {
        return modelKnowledgeAllowed.isPresent();
    }

    /**
     * Returns whether the classifier has determined a mixed-source signal.
     */
    public boolean hasMixedSource() {
        return mixedSource.isPresent();
    }
}
