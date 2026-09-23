package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;
import java.util.Optional;

/**
 * Contract for evaluating whether available evidence is sufficient for
 * generation, and for producing the metadata that downstream stages need
 * to make that decision.
 *
 * <p>This is a contract only for Phase 2B-0. Implementations may use simple
 * thresholds, source coverage checks, or more sophisticated heuristics, but
 * the interface stays the same.</p>
 *
 * <p>The input includes the query, the evidence that was collected, the
 * source plan that requested them, and any evidence that was dropped during
 * construction. The output is an {@link EvidencePolicyDecision} that later
 * stages can use to decide whether to generate, refuse, or degrade.</p>
 */
public interface EvidencePolicy {

    /**
     * Evaluate whether the available evidence is sufficient for generation,
     * given the source plan and any dropped evidence.
     *
     * @param query              the original user query, never null
     * @param evidence           the evidence that was collected or made available, never null
     * @param sourcePlan         the source plan that requested the evidence, never null
     * @param droppedEvidence    evidence that was dropped during construction, may be empty
     * @return the evidence policy decision
     */
    EvidencePolicyDecision evaluate(
            String query,
            List<Evidence> evidence,
            SourcePlan sourcePlan,
            List<Evidence> droppedEvidence
    );
}
