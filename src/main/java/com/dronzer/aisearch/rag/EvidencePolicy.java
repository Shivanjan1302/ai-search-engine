package com.dronzer.aisearch.rag;

import java.util.List;

/**
 * Contract for evaluating whether available evidence is sufficient for
 * generation, and for producing deterministic metadata that downstream stages
 * need in order to generate, refuse, or degrade.
 *
 * <p>The input includes the query, the evidence that was collected, the source
 * plan that requested it, and any evidence dropped during construction. The
 * output is an {@link EvidencePolicyDecision} that later stages can consume.</p>
 */
public interface EvidencePolicy {

    /**
     * @param query original user query, never null or blank
     * @param evidence usable evidence available to generation, never null
     * @param sourcePlan source plan that requested the evidence, never null
     * @param droppedEvidence evidence considered but not made available, never null
     * @return deterministic evidence policy decision
     * @throws EvidencePolicyException if an input is invalid or malformed
     */
    EvidencePolicyDecision evaluate(
            String query,
            List<Evidence> evidence,
            SourcePlan sourcePlan,
            List<Evidence> droppedEvidence
    );
}
