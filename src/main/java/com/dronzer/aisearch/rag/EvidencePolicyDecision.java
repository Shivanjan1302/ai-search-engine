package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;
import java.util.Optional;

/**
 * The decision produced by an evidence policy evaluation.
 *
 * <p>This is a shared contract type used by {@link EvidencePolicy} and
 * {@link ProvenanceAssembler}. It is defined as a top-level record so that
 * {@link ProvenanceAssembler} can reference it without depending on the
 * enclosing declaration of {@link EvidencePolicy}.</p>
 */
public record EvidencePolicyDecision(
        /**
         * Whether the policy considers the evidence sufficient for generation.
         */
        boolean sufficient,

        /**
         * A reason describing the decision.
         */
        String reason,

        /**
         * Which sources contributed useful evidence.
         */
        List<KnowledgeSource> sourcesWithEvidence,

        /**
         * Whether any required source was unavailable.
         */
        boolean requiredSourceUnavailable,

        /**
         * Whether there is evidence of source conflict.
         */
        boolean conflictDetected,

        /**
         * Optional metadata about the evidence coverage, such as counts
         * per source or coverage percentages.
         */
        Optional<String> coverageSummary
) {
}
