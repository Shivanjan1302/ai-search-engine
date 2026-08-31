package com.dronzer.aisearch.service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.model.EmbeddingVector;
import com.dronzer.aisearch.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;

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

    public void createEmbedding(DocumentChunk chunk) {
        EmbeddingVector vector = aiClient.generateDocumentEmbedding(chunk.getChunkText());
        vectorSearchRepository.upsertEmbedding(chunk.getId(), vector);
    }
}
