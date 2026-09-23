package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;
import java.util.Optional;

// EvidencePolicyDecision, GenerationResult, and ValidationResult are
// top-level contract types referenced from ProvenanceAssembler.

/**
 * Contract for assembling provenance metadata for the final response.
 *
 * <p>This is a contract only for Phase 2B-0. It captures what sources were
 * consulted, what evidence was used, what was dropped, what the source plan
 * requested, and any failures that occurred. This information can be used by
 * the frontend to explain where the answer came from, by audit logs to track
 * pipeline behavior, and by later phases to improve source selection.</p>
 *
 * <p>Provenance must never expose sensitive document contents, API keys, JWTs,
 * or raw retrieved text beyond what is necessary to explain the answer to the
 * user.</p>
 */
public interface ProvenanceAssembler {

    /**
     * Assemble provenance metadata for a completed pipeline run.
     *
     * @param sourcePlan        the source plan that drove the run, never null
     * @param evidenceUsed      the evidence that was presented to generation, never null
     * @param evidenceDropped   evidence that was considered but not used, may be empty
     * @param evidencePolicyDecision the evidence policy decision, may be null
     * @param generationResult  the generation result, may be null if generation was skipped
     * @param validationResult  the citation/claim validation result, may be null
     * @param failures          any pipeline failures that occurred, may be empty
     * @return the assembled provenance
     */
    Provenance assemble(
            SourcePlan sourcePlan,
            List<Evidence> evidenceUsed,
            List<Evidence> evidenceDropped,
            Optional<EvidencePolicyDecision> evidencePolicyDecision,
            Optional<GenerationResult> generationResult,
            Optional<ValidationResult> validationResult,
            List<PipelineFailure> failures
    );

    /**
     * The assembled provenance for a response.
     */
    record Provenance(
            /**
             * The source plan that drove the pipeline.
             */
            SourcePlan sourcePlan,

            /**
             * A summary of which knowledge sources contributed evidence.
             */
            List<KnowledgeSource> sourcesContributing,

            /**
             * Counts of evidence per source, if available.
             */
            Optional<EvidenceCounts> evidenceCounts,

            /**
             * The evidence policy decision that governed generation.
             */
            Optional<EvidencePolicyDecision> evidencePolicyDecision,

            /**
             * Whether generation was attempted.
             */
            boolean generationAttempted,

            /**
             * Whether generation refused to answer.
             */
            boolean generationRefused,

            /**
             * Whether citation/claim validation was performed.
             */
            boolean validationPerformed,

            /**
             * Whether the final answer passed validation.
             */
            boolean validationPassed,

            /**
             * Any pipeline failures that occurred.
             */
            List<PipelineFailure> failures,

            /**
             * Optional human-readable provenance summary for the user.
             */
            Optional<String> userSummary
    ) {
    }

    /**
     * Counts of evidence per source.
     */
    record EvidenceCounts(
            int documentCount,
            int webCount,
            int modelKnowledgeCount
    ) {
    }

    /**
     * A failure that occurred during pipeline execution.
     */
    record PipelineFailure(
            FailureKind kind,
            String message,
            Optional<String> source,
            Optional<String> detail
    ) {
    }

    /**
     * The kind of pipeline failure.
     */
    enum FailureKind {
        RETRIEVAL_FAILURE,
        WEB_PROVIDER_FAILURE,
        GENERATION_FAILURE,
        INVALID_INSUFFICIENT_EVIDENCE,
        SOURCE_UNAVAILABLE,
        RERANKING_FAILURE,
        CONTEXT_CONSTRUCTION_FAILURE,
        VALIDATION_FAILURE,
        SOURCE_PLANNING_FAILURE,
        OTHER
    }
}
