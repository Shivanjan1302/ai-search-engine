package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;
import java.util.Optional;

/**
 * Contract for building a generation-ready context from a set of evidence
 * candidates.
 *
 * <p>This is a contract only for Phase 2B-0. Later phases will implement a
 * token-aware, source-priority-aware, deduplication-aware, possibly
 * diversity-aware context builder behind this interface without changing
 * the pipeline that calls it.</p>
 *
 * <p>The builder receives a collection of evidence and a query, optionally
 * a reranking output, and produces a {@link ConstructionResult} that contains
 * the ordered context pieces, metadata, and any evidence that was dropped.
 * Implementations must preserve the source identity and provenance of each
 * piece of evidence so that later stages can distinguish document evidence
 * from web evidence.</p>
 */
public interface ContextBuilder {

    /**
     * Build a generation context from the given evidence.
     *
     * @param query         the original user query, never null
     * @param evidence      the evidence candidates to consider, never null, may be empty
     * @param rerankResult  optional reranking output, may be null if reranking was skipped
     * @param config        configuration for this construction, may be null
     * @return the construction result
     * @throws ContextConstructionException if context construction fails
     */
    ConstructionResult build(String query, Iterable<Evidence> evidence, Optional<RerankResult> rerankResult, ContextBuilderConfig config);

    /**
     * Configuration for a single context construction call.
     */
    record ContextBuilderConfig(
            /**
             * Maximum number of evidence pieces to include in the context,
             * or {@code null} for implementation-defined behavior.
             */
            Integer maxEvidencePieces,

            /**
             * Token budget target, or {@code null} for implementation-defined
             * behavior. Implementations may treat this as a soft target rather
             * than a hard limit.
             */
            Integer targetTokenBudget,

            /**
             * Whether to attempt diversity across sources when multiple
             * sources are available.
             */
            boolean diversifyAcrossSources,

            /**
             * Whether to include neighboring chunks for document evidence.
             */
            boolean includeNeighboringChunks,

            /**
             * Optional pointer to the {@link SourcePlan} that produced the
             * evidence, for source-priority decisions.
             */
            Optional<SourcePlan> sourcePlan
    ) {
    }

    /**
     * The output of a context construction.
     */
    record ConstructionResult(
            /** The ordered context pieces to present to generation/citation. */
            List<ContextPiece> pieces,

            /** Evidence that was considered but not included. */
            List<Evidence> droppedEvidence,

            /** Summary metadata about the construction. */
                                    ConstructionMetadata metadata
    ) {
    }

    /**
     * Metadata about a context construction operation.
     */
    record ConstructionMetadata(
            int piecesIncluded,
            int piecesDropped,
            int estimatedTokenCount,
            Optional<SourcePlan> sourcePlan,
            Optional<String> droppedReasonSummary
    ) {
    }
}
