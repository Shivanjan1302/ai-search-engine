package com.dronzer.aisearch.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.DocumentChunkRepository;

class ChunkServiceTest {

    @Test
    void createsAnEmbeddingForEverySavedChunk() {
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(chunkRepository.save(any(DocumentChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChunkService chunkService = new ChunkService(chunkRepository, embeddingService);
        Document document = new Document("notes.txt", "Searchable document content");
        User owner = new User("owner@example.test", "password", null);
        ReflectionTestUtils.setField(owner, "id", 1L);
        document.setUser(owner);

        chunkService.createChunks(document);

        verify(chunkRepository, times(1)).save(any(DocumentChunk.class));
        verify(embeddingService, times(1)).createEmbedding(any(DocumentChunk.class),
            org.mockito.ArgumentMatchers.eq(1L));
    }

            @Test
            void rejectsDocumentsThatWouldExceedTheConfiguredChunkLimit() {
            ChunkService chunkService = new ChunkService(
                mock(DocumentChunkRepository.class), mock(EmbeddingService.class));
            ReflectionTestUtils.setField(chunkService, "maxChunks", 2);

            Document document = new Document("notes.txt", "x".repeat(1001));
            User owner = new User("owner@example.test", "password", null);
            ReflectionTestUtils.setField(owner, "id", 1L);
            document.setUser(owner);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> chunkService.createChunks(document))
                .withMessage("Document contains too many chunks");
            }
}
