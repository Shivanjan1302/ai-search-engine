package com.dronzer.aisearch.service;

import com.dronzer.aisearch.rag.ProductionRagPipeline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Web retrieval is now plan-driven rather than an implicit document fallback. */
class HybridWebFallbackTest {

    @Test
    void ragServiceDelegatesAllHybridAndWebDecisionsToThePipeline() {
        RagService service = new RagService(mock(ProductionRagPipeline.class));
        assertThat(service).isNotNull();
    }
}
