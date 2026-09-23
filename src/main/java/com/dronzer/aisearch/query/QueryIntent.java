package com.dronzer.aisearch.query;

/**
 * A deliberately open-ended intent taxonomy for interpreted queries.
 *
 * <p>This is a contract only for Phase 2B-0. The values below are placeholders
 * that capture the kinds of queries the source planner will eventually need to
 * distinguish. Later phases may replace or expand this enum without changing the
 * {@link InterpretedQuery} contract.</p>
 */
public enum QueryIntent {

    /** The query asks about the user's own documents. */
    DOCUMENT_SPECIFIC,

    /** The query is general knowledge, not tied to the user's corpus. */
    GENERAL_KNOWLEDGE,

    /** The query requires current or time-sensitive external information. */
    CURRENT_INFORMATION,

    /** The query mixes document-specific and general/current information. */
    MIXED_SOURCE,

    /** The query c nnot be classified yet. */
    UNKNOWN
}
