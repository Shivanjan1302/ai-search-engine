package com.dronzer.aisearch.query;

/** Internal state of deterministic conversation-query interpretation. */
public enum InterpretationStatus {
    /** The question is standalone or contains no context-dependent reference. */
    UNCHANGED,
    /** A context-dependent reference was resolved with high confidence. */
    RESOLVED,
    /** A context-dependent reference exists but has no safe deterministic resolution. */
    AMBIGUOUS
}
