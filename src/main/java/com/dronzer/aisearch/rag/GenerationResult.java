package com.dronzer.aisearch.rag;

import java.util.List;
import java.util.Optional;

/**
 * The output of a grounded generation call.
 *
 * <p>The context pieces are the exact pieces presented to the model. Keeping
 * them in the result is the Phase 2B-5 citation-preparation contract: the
 * Phase 2B-6 validator can match answer citation markers to the evidence ID,
 * source type, and provenance without reconstructing generator state.</p>
 */
public record GenerationResult(
        /** The generated answer text, or null if generation refused. */
        String answer,
        /** Whether the generator refused to answer. */
        boolean refused,
        /** Optional reason for the refusal, if any. */
        Optional<String> refusalReason,
        /** Optional metadata about the generation, such as model name or latency. */
        Optional<String> generationMetadata,
        /** The exact evidence blocks presented to the model, in prompt order. */
        List<ContextPiece> contextPieces,
        /** The policy decision that governed this generation. */
        Optional<EvidencePolicyDecision> evidencePolicyDecision,
        /** Model-knowledge metadata, never represented as retrieved evidence. */
        Optional<ModelKnowledgeMetadata> modelKnowledge
) {
    /** Compatibility constructor for the original four-field contract. */
    public GenerationResult(
            String answer,
            boolean refused,
            Optional<String> refusalReason,
            Optional<String> generationMetadata
    ) {
        this(answer, refused, refusalReason, generationMetadata,
                List.of(), Optional.empty(), Optional.empty());
    }

    public GenerationResult {
        if (contextPieces == null) {
            contextPieces = List.of();
        } else {
            contextPieces = List.copyOf(contextPieces);
        }
        if (refusalReason == null) {
            refusalReason = Optional.empty();
        }
        if (generationMetadata == null) {
            generationMetadata = Optional.empty();
        }
        if (evidencePolicyDecision == null) {
            evidencePolicyDecision = Optional.empty();
        }
        if (modelKnowledge == null) {
            modelKnowledge = Optional.empty();
        }
    }
}
