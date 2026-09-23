package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.query.InterpretedQuery;

/**
 * Decides which knowledge sources should be consulted for a given interpreted query.
 *
 * <p>This is a contract only for Phase 2B-0. No implementation is provided here.
 * Later phases may replace or configure the implementation behind this interface
 * without changing the pipeline that consumes a {@link SourcePlan}.</p>
 *
 * <p>Implementations must be safe to call with an {@link InterpretedQuery} whose
 * {@link InterpretedQuery#intent()} is {@link com.dronzer.aisearch.query.QueryIntent#UNKNOWN}
 * and should degrade gracefully rather than failing because the taxonomy is incomplete.</p>
 */
public interface SourcePlanner {

    /**
     * Produce a source plan for the given interpreted query.
     *
     * @param interpretedQuery the interpreted query, never null
     * @return a source plan describing which sources to consult and with what requirements
     * @throws SourcePlanningException if planning cannot be completed at all
     */
    SourcePlan plan(InterpretedQuery interpretedQuery);

}
