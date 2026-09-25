package com.dronzer.aisearch.service;

import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.rag.ProductionRagPipeline;
import org.springframework.stereotype.Service;

import com.dronzer.aisearch.query.ConversationContext;
import java.util.Optional;

/** Backward-compatible application facade for the production RAG pipeline. */
@Service
public class RagService {

    private final ProductionRagPipeline ragPipeline;

    public RagService(ProductionRagPipeline ragPipeline) {
        this.ragPipeline = ragPipeline;
    }

    public RagResponse askQuestion(String question, String email) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return ragPipeline.execute(question, email).response();
    }

    public RagResponse askQuestion(
            String question, String email, Optional<ConversationContext> conversationContext) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (conversationContext == null) {
            throw new IllegalArgumentException("conversationContext must not be null");
        }
        return ragPipeline.execute(question, email, conversationContext).response();
    }
}
