package com.dronzer.aisearch.rag;

import java.util.Optional;

/**
 * A claim that appears to be unsupported by the provided context.
 *
 * <p>This is a shared contract type used by {@link ValidationResult} and
 * indirectly by {@link CitationValidator} and {@link ProvenanceAssembler}.
 * It is defined as a top-level record so that any contract can reference it.</p>
 */
public record UnsupportedClaim(
        /** The text of the unsupported claim, if extractable. */
        String claimText,
        /** Optional position or location hint. */
        Optional<String> locationHint,
        /** Optional reason why the claim appears unsupported. */
        Optional<String> reason
) {
}
