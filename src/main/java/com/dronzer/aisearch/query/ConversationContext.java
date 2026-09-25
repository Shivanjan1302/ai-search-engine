package com.dronzer.aisearch.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        List<ConversationContext.Turn> recentTurns) {

    public static final int MAX_RECENT_TURNS = 10;
    public static final int MAX_CONTENT_LENGTH = 2_000;

    /** A single conversation turn. */
    public record Turn(
            @NotBlank(message = "role must not be blank")
            @Pattern(regexp = "user|assistant|\\s+", message = "role must be user or assistant")
            String role,
            @NotBlank(message = "content must not be blank")
            @Size(max = ConversationContext.MAX_CONTENT_LENGTH,
                    message = "content must not exceed " + ConversationContext.MAX_CONTENT_LENGTH + " characters")
            String content,

            /**
             * Optional assistant source filenames. These are client-supplied hints only:
             * a retrieval boundary must revalidate them against the authenticated user's
             * documents before using any resulting document ID.
             */
            @Size(max = DocumentContext.MAX_FILENAME_HINTS,
                    message = "sourceFilenames must not exceed "
                            + DocumentContext.MAX_FILENAME_HINTS + " entries")
            List<@NotBlank(message = "sourceFilenames must not contain blank values")
                    @Size(max = 255, message = "source filenames must not exceed 255 characters")
                    String> sourceFilenames) {

        public Turn(String role, String content) {
            this(role, content, List.of());
        }

        public Turn {
            sourceFilenames = sourceFilenames == null
                    ? List.of()
                    : List.copyOf(sourceFilenames);
        }
    }

    public static ConversationContext empty() {
        return new ConversationContext(Optional.empty(), List.of());
    }

    public static ConversationContext of(String conversationId, List<Turn> recentTurns) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        return new ConversationContext(Optional.of(conversationId), recentTurns);
    }

    public static ConversationContext fromRecentTurns(List<Turn> recentTurns) {
        return new ConversationContext(Optional.empty(), recentTurns);
    }

    public ConversationContext {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        if (recentTurns == null) {
            throw new IllegalArgumentException("recentTurns must not be null");
        }
        if (recentTurns.size() > MAX_RECENT_TURNS) {
            throw new IllegalArgumentException(
                    "recentTurns must not exceed " + MAX_RECENT_TURNS + " turns");
        }
        if (recentTurns.stream().anyMatch(turn -> turn == null)) {
            throw new IllegalArgumentException("recentTurns must not contain null turns");
        }
        for (Turn turn : recentTurns) {
            if (turn.role() == null || turn.role().isBlank()) {
                throw new IllegalArgumentException("role must not be blank");
            }
            if (!"user".equals(turn.role()) && !"assistant".equals(turn.role())) {
                throw new IllegalArgumentException("role must be user or assistant");
            }
            if (turn.content() == null || turn.content().isBlank()) {
                throw new IllegalArgumentException("content must not be blank");
            }
            if (turn.content().length() > MAX_CONTENT_LENGTH) {
                throw new IllegalArgumentException(
                        "content must not exceed " + MAX_CONTENT_LENGTH + " characters");
            }
            if (turn.sourceFilenames().size() > DocumentContext.MAX_FILENAME_HINTS) {
                throw new IllegalArgumentException(
                        "sourceFilenames must not exceed "
                                + DocumentContext.MAX_FILENAME_HINTS + " entries");
            }
            if (turn.sourceFilenames().stream().anyMatch(filename -> filename.length() > 255)) {
                throw new IllegalArgumentException(
                        "source filenames must not exceed 255 characters");
            }
        }
        recentTurns = List.copyOf(recentTurns);
    }
}
