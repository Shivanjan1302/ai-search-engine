package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The deterministic decision produced by an {@link EvidencePolicy}.
 *
 * <p>{@code sourcesWithEvidence} contains only retrieved evidence sources.
 * MODEL_KNOWLEDGE is represented independently by {@code modelKnowledgeAllowed}.</p>
 */
public record EvidencePolicyDecision(
        boolean sufficient,
        String reason,
        List<KnowledgeSource> sourcesWithEvidence,
        boolean requiredSourceUnavailable,
        boolean conflictDetected,
        Optional<String> coverageSummary,
        boolean modelKnowledgeAllowed
) {
    public EvidencePolicyDecision(
            boolean sufficient,
            String reason,
            List<KnowledgeSource> sourcesWithEvidence,
            boolean requiredSourceUnavailable,
            boolean conflictDetected,
            Optional<String> coverageSummary
    ) {
        this(sufficient, reason, sourcesWithEvidence, requiredSourceUnavailable,
                conflictDetected, coverageSummary, false);
    }

    public EvidencePolicyDecision {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (coverageSummary == null) {
            throw new IllegalArgumentException("coverageSummary must not be null");
        }
        sourcesWithEvidence = List.copyOf(Objects.requireNonNull(
                sourcesWithEvidence, "sourcesWithEvidence must not be null"));
    }
}
