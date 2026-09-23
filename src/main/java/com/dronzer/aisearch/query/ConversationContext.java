package com.dronzer.aisearch.query;

import java.util.List;
import java.util.Optional;

/**
 * Optional conversation context for future multi-turn support.
 *
 * <p>This is a contract only for Phase 2B-0. No persistence or rewriting is implemented.
 * The shape exists so that later phases can attach conversation history and a session
 * identifier without changing the core pipeline contracts.</p>
 */
public record ConversationContext(

        /** Optional identifier for the conversation/session. */
        Optional<String> conversationId,

        /**
         * Recent turns, oldest first. Empty by default.
         * For 2B-0 these are plain role/content pairs; future phases may replace
         * this with a richer turn model.
         */
        List<Turn> recentTurns

) {

    /** A single conversation turn. */
    public record Turn(String role, String content) {
        public Turn {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("role must not be blank");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("content must not be blank");
            }
        }
    }

    public static ConversationContext empty() {
        return new ConversationContext(Optional.empty(), List.of());
    }

    public static ConversationContext of(String conversationId, List<Turn> recentTurns) {
        return new ConversationContext(Optional.of(conversationId), recentTurns);
    }
}
