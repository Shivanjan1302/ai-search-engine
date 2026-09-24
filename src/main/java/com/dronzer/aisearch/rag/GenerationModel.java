package com.dronzer.aisearch.rag;

/**
 * Small model-generation boundary used by {@link DefaultGroundedGenerator}.
 * The system/user split is retained here even when an underlying provider
 * accepts one text payload.
 */
public interface GenerationModel {

    String generate(GenerationPrompt prompt);

    /** Structured prompt passed to the model. */
    record GenerationPrompt(String systemPrompt, String userPrompt) {
        public GenerationPrompt {
            if (systemPrompt == null || systemPrompt.isBlank()) {
                throw new IllegalArgumentException("systemPrompt must not be blank");
            }
            if (userPrompt == null || userPrompt.isBlank()) {
                throw new IllegalArgumentException("userPrompt must not be blank");
            }
        }
    }
}
