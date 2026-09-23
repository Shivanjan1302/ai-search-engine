package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.dto.KnowledgeSource;
import java.util.List;

/**
 * The source planner's decision for a single interpreted query.
 *
 * <p>This is a contract only for Phase 2B-0. No planner implementation exists yet.
 * The shape is intended to let future phases express, in one place, which sources
 * should be consulted for a given query and with what precedence.</p>
 *
 * <p>Source requirements are expressed per {@link KnowledgeSource} so that the planner
 * can declare, for example, that documents are required, web is optional, and model
 * knowledge is permitted but not required.</p>
 */
public record SourcePlan(

        /** Ordered list of source requirements, in priority order. */
        List<SourceRequirement> sources,

        /**
         * Whether the query is expected to need multiple source types.
         * When {@code true}, later phases should expect to combine evidence
         * from more than one source.
         */
        boolean mixedSource,

        /**
         * Whether fresh/current information is required.
         * When {@code true}, web retrieval should be preferred even if
         * documents are the primary source.
         */
        boolean requiresFreshness,

        /**
         * How much evidence is required before generation may proceed.
         * A stricter policy reduces hallucination risk but may increase
         * "I don't know" responses.
         */
        EvidenceRequirement evidenceRequirement,

        /**
         * What to do when a required source is unavailable.
         * {@code FAIL_FAST} means the pipeline should report the failure
         * rather than silently degrading.
         */
        FallbackPolicy fallbackPolicy

) {

    public SourcePlan {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        for (SourceRequirement requirement : sources) {
            if (requirement == null) {
                throw new IllegalArgumentException("sources must not contain null elements");
            }
        }
    }

    /**
     * Copy with an appended source requirement.
     */
    public SourcePlan withAppendedSource(SourceRequirement source) {
        List<SourceRequirement> updated = List.copyOf(sources);
        updated.add(source);
        return new SourcePlan(updated, mixedSource, requiresFreshness, evidenceRequirement, fallbackPolicy);
    }

    /**
     * Copy with mixed source requirement toggled.
     */
    public SourcePlan withMixedSource(boolean mixedSource) {
        return new SourcePlan(sources, mixedSource, requiresFreshness, evidenceRequirement, fallbackPolicy);
    }

    /**
     * Copy with freshness requirement toggled.
     */
    public SourcePlan withFreshness(boolean requiresFreshness) {
        return new SourcePlan(sources, mixedSource, requiresFreshness, evidenceRequirement, fallbackPolicy);
    }
}
