package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.query.ConversationContext;
import java.util.List;
import java.util.Optional;

/**
 * Contract for grounded generation.
 *
 * <p>Generation consumes the policy decision and the already-built context. It
 * does not retrieve, rerank, validate citations, or assemble provenance. The
 * request keeps the original query and every supplied context piece intact so a
 * later citation stage can map a model citation back to the exact evidence.</p>
 */
public interface GroundedGenerator {

    /**
     * Generate a grounded answer for the given request.
     *
     * @param request the generation request, never null
     * @return a grounded result or an explicit insufficient-evidence result
     * @throws GenerationException if input validation, prompt construction, or
     *         model invocation fails
     */
    GenerationResult generate(GenerationRequest request);

    /**
     * A request to the grounded generator.
     *
     * <p>The source plan and policy decision are deliberately explicit. A
     * generator must not infer required evidence from whatever happens to be
     * present in the context.</p>
     */
    record GenerationRequest(
            String userQuery,
            Optional<String> normalizedQuery,
            Optional<ConversationContext> conversationContext,
            List<ContextPiece> contextPieces,
            Optional<GenerationPolicy> policy,
            Optional<ModelKnowledgeMetadata> modelKnowledge,
            SourcePlan sourcePlan,
            EvidencePolicyDecision evidencePolicyDecision
    ) {
        /**
         * Compatibility constructor for the original Phase 2B-0 contract.
         * The two newly required policy inputs remain null and are rejected by
         * the Phase 2B-5 generator when supplied through this constructor.
         */
        public GenerationRequest(
                String userQuery,
                Optional<String> normalizedQuery,
                Optional<ConversationContext> conversationContext,
                List<ContextPiece> contextPieces,
                Optional<GenerationPolicy> policy,
                Optional<ModelKnowledgeMetadata> modelKnowledge
        ) {
            this(userQuery, normalizedQuery, conversationContext, contextPieces,
                    policy, modelKnowledge, null, null);
        }
    }

    /**
     * Optional generation hints retained from the original contract. The
     * evidence policy decision remains authoritative over these hints.
     */
    record GenerationPolicy(
            boolean refuseOnWeakEvidence,
            boolean discloseModelKnowledge,
            Optional<String> sourceMixInstructions
    ) {
    }
}
