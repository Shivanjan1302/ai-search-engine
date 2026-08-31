package com.dronzer.aisearch.service;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;

@Service
public class ChunkService {

    private final DocumentChunkRepository chunkRepository;

    private final EmbeddingService embeddingService;

    public ChunkService(
            DocumentChunkRepository chunkRepository,
            EmbeddingService embeddingService) {

        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    public void createChunks(
            Document document) {

        String content =
                document.getContent();

        int chunkSize = 500;

        int index = 0;

        for (int i = 0;
             i < content.length();
             i += chunkSize) {

            int end =
                    Math.min(
                            i + chunkSize,
                            content.length());

            String chunkText =
                    content.substring(
                            i,
                            end);

            DocumentChunk chunk =
                    new DocumentChunk();

            chunk.setChunkText(
                    chunkText);

            chunk.setChunkIndex(
                    index++);

            chunk.setDocument(
                    document);

            DocumentChunk savedChunk = chunkRepository.save(chunk);

            embeddingService.createEmbedding(savedChunk);
        }
    }
}
