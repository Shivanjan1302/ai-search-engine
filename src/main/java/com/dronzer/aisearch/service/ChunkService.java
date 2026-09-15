package com.dronzer.aisearch.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.DocumentChunkRepository;

@Service
public class ChunkService {

    private final DocumentChunkRepository chunkRepository;

    private final EmbeddingService embeddingService;

        private final DocumentChunker documentChunker;

        @Value("${app.document.max-chunks:10000}")
        private int maxChunks = 10000;

    public ChunkService(
            DocumentChunkRepository chunkRepository,
                        EmbeddingService embeddingService,
                        DocumentChunker documentChunker) {

        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
                this.documentChunker = documentChunker;
    }

    public void createChunks(
            Document document) {

                User owner = document.getUser();
                if (owner == null || owner.getId() == null) {
                        throw new IllegalArgumentException("Document owner is required");
                }

        String content =
                document.getContent();

        List<String> chunks = documentChunker.split(content);
        int chunkCount = chunks.size();
        if (chunkCount > maxChunks) {
                        throw new IllegalArgumentException("Document contains too many chunks");
        }

        int index = 0;

        for (String chunkText : chunks) {

            DocumentChunk chunk =
                    new DocumentChunk();

            chunk.setChunkText(
                    chunkText);

            chunk.setChunkIndex(
                    index++);

            chunk.setDocument(
                    document);

            DocumentChunk savedChunk = chunkRepository.save(chunk);

            embeddingService.createEmbedding(savedChunk, owner.getId());
        }
    }
}
