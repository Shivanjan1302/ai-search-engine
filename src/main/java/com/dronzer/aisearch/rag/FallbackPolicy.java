package com.dronzer.aisearch.rag;

/**
 * How the pipeline should behave when a required source is unavailable.
 *
 * <p>This is a contract only for Phase 2B-0.</p>
 */
public enum FallbackPolicy {

    /**
     * If a required source cannot be consulted, fail fast and report the
     * failure to the caller rather than silently falling back to a weaker
     * source or to model knowledge alone.
     */
    FAIL_FAST,

    /**
     * If a required source cannot be consulted, the pipeline may attempt to
     * continue with weaker sources or with model knowledge, but it must
     * record that the required source was unavailable so that downstream
     * components can decide how to present the answer.
     */
    DEGRADE_GRADUALLY
}
