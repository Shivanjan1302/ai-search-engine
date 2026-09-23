package com.dronzer.aisearch.rag;

import java.util.List;
import java.util.Optional;

/**
 * The result of a citation/claim validation.
 *
 * <p>This is a shared contract type used by {@link CitationValidator} and
 * {@link ProvenanceAssembler}. It is defined as a top-level record so that
 * {@link ProvenanceAssembler} can reference it without depending on the
 * enclosing declaration of {@link CitationValidator}.</p>
 */
public record ValidationResult(
        /**
         * Whether the answer is fully supported by the provided context.
         */
        boolean fullySupported,

        /**
         * Whether the answer contains statements that appear to be
         * unsupported by the provided context.
         */
        boolean unsupportedClaimsDetected,

        /**
         * Human-readable validation summary.
         */
        String summary,

        /**
         * Optional list of detected unsupported claims, if any.
         */
        Optional<List<UnsupportedClaim>> unsupportedClaims
) {
}
