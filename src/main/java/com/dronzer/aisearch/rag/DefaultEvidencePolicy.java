package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stateless policy that evaluates available evidence against a {@link SourcePlan}.
 *
 * <p>This is a pure decision function. It never retrieves, reranks, reads
 * scores, accesses tenant data, calls a provider, invokes a model, or
 * manufactures evidence. Each REQUIRED retrievable source is evaluated
 * independently. MODEL_KNOWLEDGE is never counted as retrieved evidence.</p>
 */
public final class DefaultEvidencePolicy implements EvidencePolicy {

    public static DefaultEvidencePolicy create() {
        return new DefaultEvidencePolicy();
    }

    @Override
    public EvidencePolicyDecision evaluate(
            String query,
            List<Evidence> evidence,
            SourcePlan sourcePlan,
            List<Evidence> droppedEvidence
    ) {
        validate(query, evidence, sourcePlan, droppedEvidence);

        Counts counts = countPlannedEvidence(sourcePlan.sources(), evidence);
        List<KnowledgeSource> missingRequired = missingRequired(sourcePlan.sources(), counts);
        boolean modelAllowed = modelKnowledgeAllowed(sourcePlan.sources());
        boolean sufficient = missingRequired.isEmpty()
                && (hasRetrievedEvidence(counts) || modelAllowed);
        List<KnowledgeSource> sourcesWithEvidence = sourcesWithEvidence(
                sourcePlan.sources(), counts);

        return new EvidencePolicyDecision(
                sufficient,
                reason(sufficient, missingRequired, counts, modelAllowed),
                sourcesWithEvidence,
                !missingRequired.isEmpty(),
                false,
                Optional.of(coverageSummary(counts, droppedEvidence.size(), modelAllowed)),
                modelAllowed
        );
    }

    private static void validate(
            String query,
            List<Evidence> evidence,
            SourcePlan sourcePlan,
            List<Evidence> droppedEvidence
    ) {
        if (query == null || query.isBlank()) {
            throw failure("query must not be null or blank", null);
        }
        if (evidence == null) {
            throw failure("evidence must not be null", null);
        }
        if (sourcePlan == null) {
            throw failure("sourcePlan must not be null", null);
        }
        if (droppedEvidence == null) {
            throw failure("droppedEvidence must not be null", null);
        }
        validateEvidence(evidence, "evidence");
        validateEvidence(droppedEvidence, "droppedEvidence");
        validatePlan(sourcePlan);
    }

    private static void validatePlan(SourcePlan plan) {
        if (plan.evidenceRequirement() == null) {
            throw failure("sourcePlan.evidenceRequirement must not be null", null);
        }
        if (plan.fallbackPolicy() == null) {
            throw failure("sourcePlan.fallbackPolicy must not be null", null);
        }

        Map<KnowledgeSource, Boolean> seen = new EnumMap<>(KnowledgeSource.class);
        for (int index = 0; index < plan.sources().size(); index++) {
            SourceRequirement requirement = plan.sources().get(index);
            if (requirement.source() == null) {
                throw failure("sourcePlan.sources[" + index + "].source must not be null", null);
            }
            if (requirement.level() == null) {
                throw failure("sourcePlan.sources[" + index + "].level must not be null", null);
            }
            if (seen.put(requirement.source(), Boolean.TRUE) != null) {
                throw failure("sourcePlan.sources must contain each source at most once: "
                        + requirement.source(), null);
            }
            if (requirement.level() == RequirementLevel.REQUIRED && !requirement.permitted()) {
                throw failure("required source must be permitted: " + requirement.source(), null);
            }
        }
    }

    private static void validateEvidence(List<Evidence> evidence, String name) {
        for (int index = 0; index < evidence.size(); index++) {
            Evidence item = evidence.get(index);
            if (item == null) {
                throw failure(name + "[" + index + "] must not be null", null);
            }
            try {
                if (item.source() == null) {
                    throw failure(name + "[" + index + "].source must not be null", null);
                }
                if (item.source() == KnowledgeSource.MODEL_KNOWLEDGE) {
                    throw failure(name + "[" + index + "] must not represent model knowledge as evidence", null);
                }
                if (item.id() == null || item.id().isBlank()) {
                    throw failure(name + "[" + index + "].id must not be null or blank", null);
                }
                if (item.content() == null || item.content().isBlank()) {
                    throw failure(name + "[" + index + "].content must not be null or blank", null);
                }
            } catch (EvidencePolicyException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw failure("unable to read " + name + "[" + index + "]", exception);
            }
        }
    }

    private static Counts countPlannedEvidence(
            List<SourceRequirement> plan,
            List<Evidence> evidence
    ) {
        int documents = 0;
        int web = 0;
        for (Evidence item : evidence) {
            for (SourceRequirement requirement : plan) {
                if (requirement.permitted() && requirement.source() == item.source()) {
                    if (item.source() == KnowledgeSource.DOCUMENT) {
                        documents++;
                    } else if (item.source() == KnowledgeSource.WEB) {
                        web++;
                    }
                    break;
                }
            }
        }
        return new Counts(documents, web);
    }

    private static List<KnowledgeSource> sourcesWithEvidence(
            List<SourceRequirement> plan,
            Counts counts
    ) {
        List<KnowledgeSource> result = new ArrayList<>(2);
        for (SourceRequirement requirement : plan) {
            if (requirement.permitted() && counts.has(requirement.source())) {
                result.add(requirement.source());
            }
        }
        return List.copyOf(result);
    }

    private static List<KnowledgeSource> missingRequired(
            List<SourceRequirement> plan,
            Counts counts
    ) {
        List<KnowledgeSource> result = new ArrayList<>();
        for (SourceRequirement requirement : plan) {
            if (requirement.level() == RequirementLevel.REQUIRED
                    && requirement.source() != KnowledgeSource.MODEL_KNOWLEDGE
                    && !counts.has(requirement.source())) {
                result.add(requirement.source());
            }
        }
        return List.copyOf(result);
    }

    private static boolean modelKnowledgeAllowed(List<SourceRequirement> plan) {
        for (SourceRequirement requirement : plan) {
            if (requirement.source() == KnowledgeSource.MODEL_KNOWLEDGE) {
                return requirement.permitted();
            }
        }
        return false;
    }

    private static boolean hasRetrievedEvidence(Counts counts) {
        return counts.documents > 0 || counts.web > 0;
    }

    private static String reason(
            boolean sufficient,
            List<KnowledgeSource> missingRequired,
            Counts counts,
            boolean modelAllowed
    ) {
        if (!missingRequired.isEmpty()) {
            return "INSUFFICIENT_REQUIRED_EVIDENCE: missing required sources " + missingRequired;
        }
        if (sufficient && hasRetrievedEvidence(counts)) {
            return "SUFFICIENT_EVIDENCE: usable retrieved evidence is available";
        }
        if (sufficient) {
            return "SUFFICIENT_MODEL_KNOWLEDGE: no retrieved evidence is required and model knowledge is permitted";
        }
        return "INSUFFICIENT_NO_EVIDENCE: no usable retrieved evidence and model knowledge is not permitted";
    }

    private static String coverageSummary(Counts counts, int dropped, boolean modelAllowed) {
        return "document=" + counts.documents
                + "; web=" + counts.web
                + "; dropped=" + dropped
                + "; modelKnowledgeAllowed=" + modelAllowed;
    }

    private static EvidencePolicyException failure(String message, Throwable cause) {
        return cause == null
                ? new EvidencePolicyException(message)
                : new EvidencePolicyException(message, cause);
    }

    private record Counts(int documents, int web) {
        private boolean has(KnowledgeSource source) {
            return (source == KnowledgeSource.DOCUMENT && documents > 0)
                    || (source == KnowledgeSource.WEB && web > 0);
        }
    }
}
