package com.dronzer.aisearch.service;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkServiceTest {

    @Test
    void createsAnEmbeddingForEverySavedChunk() {
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(chunkRepository.save(any(DocumentChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChunkService chunkService = new ChunkService(chunkRepository, embeddingService);
        Document document = new Document("notes.txt", "Searchable document content");

        chunkService.createChunks(document);

        verify(chunkRepository, times(1)).save(any(DocumentChunk.class));
        verify(embeddingService, times(1)).createEmbedding(any(DocumentChunk.class));
    }
}
