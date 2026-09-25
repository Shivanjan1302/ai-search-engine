package com.dronzer.aisearch.service;

import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.query.ConversationContext;

import com.dronzer.aisearch.rag.ProductionRagPipeline;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final ProductionRagPipeline pipeline = mock(ProductionRagPipeline.class);
    private final RagService service = new RagService(pipeline);

    @Test
    void delegatesQuestionAndTenantToTheAuthoritativePipeline() {
        RagResponse expected = new RagResponse("Grounded", List.of());
        when(pipeline.execute("What does my document say?", "tenant@example.test"))
                .thenReturn(new ProductionRagPipeline.PipelineResult(
                        expected, mock(com.dronzer.aisearch.rag.ProvenanceAssembler.Provenance.class)));

        RagResponse actual = service.askQuestion(
                "What does my document say?", "tenant@example.test");

        assertThat(actual).isSameAs(expected);
        verify(pipeline).execute("What does my document say?", "tenant@example.test");
    }

    @Test
    void delegatesConversationContextToTheAuthoritativePipeline() {
        RagResponse expected = new RagResponse("Grounded", List.of());
        ConversationContext context = ConversationContext.fromRecentTurns(List.of(
                new ConversationContext.Turn("user", "Earlier question")));
        when(pipeline.execute("What does my document say?", "tenant@example.test", Optional.of(context)))
                .thenReturn(new ProductionRagPipeline.PipelineResult(
                        expected, mock(com.dronzer.aisearch.rag.ProvenanceAssembler.Provenance.class)));

        RagResponse actual = service.askQuestion(
                "What does my document say?", "tenant@example.test", Optional.of(context));

        assertThat(actual).isSameAs(expected);
        verify(pipeline).execute("What does my document say?", "tenant@example.test", Optional.of(context));
    }

    @Test
    void preservesBlankQuestionValidation() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.askQuestion("  ", "tenant@example.test"))
                .withMessage("question must not be blank");
    }
}
