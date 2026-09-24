package com.dronzer.aisearch.service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.rag.ProductionRagPipeline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Hybrid ranking remains covered by HybridRetrievalService and pipeline tests. */
class HybridRagServiceTest {

    @Test
    void ragServiceContainsNoDirectHybridRetrievalDependency() {
        assertThat(RagService.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == ProductionRagPipeline.class)
                .noneMatch(field -> field.getType() == HybridRetrievalService.class);
        assertThat(mock(AIClient.class)).isNotNull();
    }
}
