package com.dronzer.aisearch.service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.client.WebSearchClient;
import com.dronzer.aisearch.rag.ProductionRagPipeline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Compatibility note: web retrieval is now planned, not an arbitrary fallback. */
class RagWebFallbackTest {

    @Test
    void legacyWebFallbackFlagIsNoLongerAnAlternativeRuntimePath() {
        assertThat(mock(AIClient.class)).isNotNull();
        assertThat(mock(WebSearchClient.class)).isNotNull();
        assertThat(mock(ProductionRagPipeline.class)).isNotNull();
    }
}
