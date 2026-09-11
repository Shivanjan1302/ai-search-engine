package com.dronzer.aisearch.service;

import org.springframework.stereotype.Service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.model.EmbeddingVector;
import com.dronzer.aisearch.repository.VectorSearchRepository;

@Service
public class EmbeddingService {

    private final AIClient aiClient;

    private final VectorSearchRepository vectorSearchRepository;

    public EmbeddingService(
            AIClient aiClient,
            VectorSearchRepository vectorSearchRepository) {

        this.aiClient = aiClient;
        this.vectorSearchRepository = vectorSearchRepository;
    }

    public void createEmbedding(DocumentChunk chunk, Long userId) {
        EmbeddingVector vector = aiClient.generateDocumentEmbedding(chunk.getChunkText());
        int updatedRows = vectorSearchRepository.upsertEmbedding(
                chunk.getId(), userId, vector);
        if (updatedRows != 1) {
            throw new IllegalStateException("Chunk is not owned by the requested user");
        }
    }
}
