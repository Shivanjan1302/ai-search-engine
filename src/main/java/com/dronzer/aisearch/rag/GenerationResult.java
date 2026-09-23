package com.dronzer.aisearch.rag;

import java.util.Optional;

/**
 * The output of a grounded generation call.
 *
 * <p>This is a shared contract type used by {@link GroundedGenerator} and
 * {@link ProvenanceAssembler}. It is defined as a top-level record so that
 * {@link ProvenanceAssembler} can reference it without depending on the
 * enclosing declaration of {@link GroundedGenerator}.</p>
 */
public record GenerationResult(
        /** The generated answer text, or null if generation refused. */
        String answer,

        /** Whether the generator refused to answer. */
        boolean refused,

        /** Optional reason for the refusal, if any. */
        Optional<String> refusalReason,

        /** Optional metadata about the generation, such as model name or latency. */
        Optional<String> generationMetadata
) {
}
