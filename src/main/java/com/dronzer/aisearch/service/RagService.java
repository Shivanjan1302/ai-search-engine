package com.dronzer.aisearch.service;

import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.rag.ProductionRagPipeline;
import org.springframework.stereotype.Service;

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
}
