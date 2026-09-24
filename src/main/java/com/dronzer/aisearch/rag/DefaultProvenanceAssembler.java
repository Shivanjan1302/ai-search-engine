package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic, content-free aggregation of pipeline provenance. */
public final class DefaultProvenanceAssembler implements ProvenanceAssembler {

    @Override
    public Provenance assemble(
            SourcePlan sourcePlan,
            List<Evidence> evidenceUsed,
            List<Evidence> evidenceDropped,
            Optional<EvidencePolicyDecision> evidencePolicyDecision,
            Optional<GenerationResult> generationResult,
            Optional<ValidationResult> validationResult,
            List<PipelineFailure> failures) {

        try {
            Objects.requireNonNull(sourcePlan, "sourcePlan must not be null");
            List<Evidence> used = List.copyOf(Objects.requireNonNull(
                    evidenceUsed, "evidenceUsed must not be null"));
            List<Evidence> dropped = List.copyOf(Objects.requireNonNull(
                    evidenceDropped, "evidenceDropped must not be null"));
            Optional<EvidencePolicyDecision> decision = requireOptional(
                    evidencePolicyDecision, "evidencePolicyDecision");
            Optional<GenerationResult> generation = requireOptional(
                    generationResult, "generationResult");
            Optional<ValidationResult> validation = requireOptional(
                    validationResult, "validationResult");
            List<PipelineFailure> immutableFailures = List.copyOf(Objects.requireNonNull(
                    failures, "failures must not be null"));

            int documentCount = 0;
            int webCount = 0;
            Set<KnowledgeSource> contributing = new LinkedHashSet<>();
            for (Evidence evidence : used) {
                if (evidence == null) {
                    throw failure("evidenceUsed must not contain null elements", null);
                }
                if (evidence.source() == KnowledgeSource.DOCUMENT) {
                    documentCount++;
                    contributing.add(KnowledgeSource.DOCUMENT);
                } else if (evidence.source() == KnowledgeSource.WEB) {
                    webCount++;
                    contributing.add(KnowledgeSource.WEB);
                } else {
                    throw failure("model knowledge must not be represented as evidence", null);
                }
            }

            int modelKnowledgeCount = generation
                    .flatMap(GenerationResult::modelKnowledge)
                    .map(metadata -> 1)
                    .orElse(0);
            boolean generationAttempted = generation.isPresent();
            boolean generationRefused = generation.map(GenerationResult::refused).orElse(false);
            boolean validationPerformed = validation.isPresent();
            boolean validationPassed = validation.map(ValidationResult::fullySupported).orElse(false);

            String summary = "sources=" + contributing
                    + "; document=" + documentCount
                    + "; web=" + webCount
                    + "; modelKnowledge=" + modelKnowledgeCount
                    + "; dropped=" + dropped.size()
                    + "; validation=" + (validationPerformed
                    ? (validationPassed ? "passed" : "failed")
                    : "not-performed");

            return new Provenance(
                    sourcePlan,
                    List.copyOf(contributing),
                    Optional.of(new EvidenceCounts(documentCount, webCount, modelKnowledgeCount)),
                    decision,
                    generationAttempted,
                    generationRefused,
                    validationPerformed,
                    validationPassed,
                    immutableFailures,
                    Optional.of(summary));
        } catch (ProvenanceAssemblyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("could not assemble final provenance", exception);
        }
    }

    private static <T> Optional<T> requireOptional(Optional<T> value, String name) {
        if (value == null) {
            throw failure(name + " must not be null", null);
        }
        return value;
    }

    private static ProvenanceAssemblyException failure(String message, Throwable cause) {
        return cause == null
                ? new ProvenanceAssemblyException(message)
                : new ProvenanceAssemblyException(message, cause);
    }
}
