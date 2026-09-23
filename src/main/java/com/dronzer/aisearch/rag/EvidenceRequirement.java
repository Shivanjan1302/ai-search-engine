package com.dronzer.aisearch.rag;

/**
 * How much evidence the source planner expects before generation may proceed.
 *
 * <p>This is a contract only for Phase 2B-0. It controls how aggressive the
 * pipeline is about answering with weak evidence versus refusing when evidence
 * is thin.</p>
 */
public enum EvidenceRequirement {

    /**
     * Prefer answers with the best available evidence, but allow generation
     * with weak evidence if no strong evidence exists. This is the most
     * helpful but least conservative setting.
     */
    RELAXED,

    /**
     * Require at least one piece of reasonably strong evidence before
     * generation. If no such evidence exists, the pipeline should produce
     * an insufficient-evidence response rather than hallucinating.
     */
    MODERATE,

    /**
     * Require strong, verifiable evidence across the required sources before
     * generation. This is the most conservative setting and is appropriate
     * for high-stakes or authoritative answers.
     */
    STRICT
}
