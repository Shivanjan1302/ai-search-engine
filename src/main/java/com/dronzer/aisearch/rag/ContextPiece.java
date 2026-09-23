package com.dronzer.aisearch.rag;

import java.util.Optional;

/**
 * A single piece of context with its source identity preserved.
 *
 * <p>This is a shared contract type used by {@link ContextBuilder},
 * {@link CitationValidator}, and {@link GroundedGenerator}. It is defined as a
 * top-level record so that any contract can reference it without depending on
 * the enclosing declaration of another interface.</p>
 */
public record ContextPiece(

        /** The evidence this piece comes from. */
        Evidence evidence,

        /** Optional preceding context from neighboring evidence. */
        Optional<ContextPiece> preceding,

        /** Optional following context from neighboring evidence. */
        Optional<ContextPiece> following,

        /** Optional role label such as "document" or "web" for downstream use. */
        String role

) {
    public ContextPiece(Evidence evidence) {
        this(evidence, Optional.empty(), Optional.empty(), null);
    }
}
