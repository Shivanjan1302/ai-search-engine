package com.dronzer.aisearch.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentCaptor.forClass;
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

        ChunkService chunkService = new ChunkService(
            chunkRepository, embeddingService, new DocumentChunker(850, 120));
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
                mock(DocumentChunkRepository.class), mock(EmbeddingService.class),
                new DocumentChunker(850, 120));
            ReflectionTestUtils.setField(chunkService, "maxChunks", 2);

            Document document = new Document("notes.txt", "x".repeat(1701));
            User owner = new User("owner@example.test", "password", null);
            ReflectionTestUtils.setField(owner, "id", 1L);
            document.setUser(owner);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> chunkService.createChunks(document))
                .withMessage("Document contains too many chunks");
            }

            @Test
            void savesOrderedNonEmptyChunksAndEmbedsEveryChunk() {
            DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
            EmbeddingService embeddingService = mock(EmbeddingService.class);
            when(chunkRepository.save(any(DocumentChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            ChunkService chunkService = new ChunkService(
                chunkRepository, embeddingService, new DocumentChunker(850, 120));
            Document document = document("A ".repeat(600));

            chunkService.createChunks(document);

            ArgumentCaptor<DocumentChunk> captor = forClass(DocumentChunk.class);
            verify(chunkRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            List<DocumentChunk> saved = captor.getAllValues();
            assertThat(saved).isNotEmpty();
            assertThat(saved).extracting(DocumentChunk::getChunkIndex)
                .containsExactly(0, 1);
            assertThat(saved).allSatisfy(chunk -> assertThat(chunk.getChunkText()).isNotBlank());
            verify(embeddingService, org.mockito.Mockito.times(saved.size()))
                .createEmbedding(any(DocumentChunk.class), org.mockito.ArgumentMatchers.eq(1L));
            }

            private Document document(String content) {
            Document document = new Document("notes.txt", content);
            User owner = new User("owner@example.test", "password", null);
            ReflectionTestUtils.setField(owner, "id", 1L);
            document.setUser(owner);
            return document;
            }
}
