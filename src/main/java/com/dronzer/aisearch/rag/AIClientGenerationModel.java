package com.dronzer.aisearch.rag;

import com.dronzer.aisearch.client.AIClient;

/**
 * Adapter from the existing application model client to the generator boundary.
 * The legacy client accepts one prompt, so the two structured sections are
 * joined only at this transport boundary.
 */
public final class AIClientGenerationModel implements GenerationModel {
    private final AIClient aiClient;

    public AIClientGenerationModel(AIClient aiClient) {
        if (aiClient == null) {
            throw new GenerationException("generation model client must not be null");
        }
        this.aiClient = aiClient;
    }

    @Override
    public String generate(GenerationPrompt prompt) {
        if (prompt == null) {
            throw new GenerationException("generation prompt must not be null");
        }
        return aiClient.generateAnswer(prompt.systemPrompt() + "\n\n" + prompt.userPrompt());
    }
}
