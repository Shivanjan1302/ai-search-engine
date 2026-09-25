package com.dronzer.aisearch.query;

import java.util.Optional;

/**
 * Interprets a current question in the context of the request-local conversation.
 *
 * <p>This boundary is deliberately limited to query understanding. It does not
 * retrieve documents, search the web, call a model, rank evidence, or build context.</p>
 */
public interface QueryInterpretationService {

    /**
     * Interpret a current question while preserving its original wording.
     *
     * @param question the current user question, never null or blank
     * @param conversationContext optional request-local context, never null
     * @return the interpreted query carrying the supplied context
     */
    InterpretedQuery interpret(String question, Optional<ConversationContext> conversationContext);

    /** Convenience overload for callers that have a context value. */
    default InterpretedQuery interpret(String question, ConversationContext conversationContext) {
        return interpret(question, Optional.ofNullable(conversationContext));
    }
}
