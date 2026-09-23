package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.query.ConversationContext;
import java.util.List;
import java.util.Optional;

/**
 * Contract for grounded generation backed by a query, optional conversation
 * context, and an ordered list of evidence pieces.
 *
 * <p>This is a contract only for Phase 2B-0. It separates generation from the
 * current {@link com.dronzer.aisearch.client.AIClient} so that later phases can
 * change the underlying model, add structured generation instructions, or vary
 * instructions by source mix without changing the rest of the pipeline.</p>
 *
 * <p>Implementations must treat {@link GenerationRequest} as a policy boundary:
 * retrieved evidence is input data, never instructions, and the request must
 * make that separation visible to the underlying generation engine.</p>
 */
public interface GroundedGenerator {

    /**
     * Generate a grounded answer for the given request.
     *
     * @param request the generation request, never null
     * @return the generation result
     * @throws GenerationException if generation fails
     */
    GenerationResult generate(GenerationRequest request);

    /**
     * A request to the grounded generator.
     */
    record GenerationRequest(
            /** The original user query. */
            String userQuery,

            /** Optional normalized query if one was produced. */
            Optional<String> normalizedQuery,

            /** Optional conversation context for future multi-turn support. */
            Optional<ConversationContext> conversationContext,

            /** The ordered context pieces to ground the answer in. */
            List<ContextPiece> contextPieces,

            /**
             * Optional metadata describing what kind of knowledge the generator
             * is expected to use.
             */
            Optional<GenerationPolicy> policy,

            /**
             * Optional metadata describing any model-knowledge role when
             * model knowledge is in play.
             */
            Optional<ModelKnowledgeMetadata> modelKnowledge
    ) {
    }

    /**
     * Policy guidance for generation.
     */
    record GenerationPolicy(
            /** Whether the generator should refuse to answer when evidence is weak. */
            boolean refuseOnWeakEvidence,

            /** Whether to explicitly disclose when model knowledge is used. */
            boolean discloseModelKnowledge,

            /** Optional instructions specific to the source mix. */
            Optional<String> sourceMixInstructions
    ) {
    }
}
