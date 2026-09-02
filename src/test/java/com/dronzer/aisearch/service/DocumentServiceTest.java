package com.dronzer.aisearch.service;

import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.model.EmbeddingVector;
import com.dronzer.aisearch.repository.DocumentChunkRepository;
import com.dronzer.aisearch.repository.DocumentRepository;
import com.dronzer.aisearch.repository.UserRepository;
import com.dronzer.aisearch.repository.VectorSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChunkService chunkService = mock(ChunkService.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final AIClient aiClient = mock(AIClient.class);
    private final VectorSearchRepository vectorSearchRepository = mock(VectorSearchRepository.class);

    private DocumentService documentService;
    private User userA;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository,
                userRepository,
                chunkService,
                chunkRepository,
                embeddingService,
                aiClient,
                vectorSearchRepository);
        userA = user(1L, "user-a@example.test");
    }

    @Test
    void listsOnlyDocumentsOwnedByTheAuthenticatedUser() {
        Document dockerDocument = new Document("docker.txt", "Docker content");
        dockerDocument.setUser(userA);
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(documentRepository.findByUserOrderByUploadedAtDesc(userA))
                .thenReturn(List.of(dockerDocument));

        List<Document> documents = documentService.getDocuments(userA.getEmail());

        assertThat(documents).containsExactly(dockerDocument);
        verify(documentRepository).findByUserOrderByUploadedAtDesc(userA);
        verify(documentRepository, never()).findAll();
    }

    @Test
    void savesDocumentsWithTheAuthenticatedUserAsOwner() {
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Document savedDocument = documentService.saveDocument(
                "docker.txt",
                "Docker content",
                userA.getEmail());

        assertThat(savedDocument.getUser()).isSameAs(userA);
        verify(chunkService).createChunks(savedDocument);
    }

    @Test
    void scopesKeywordAndSemanticSearchToTheAuthenticatedUser() {
        Document dockerDocument = new Document("docker.txt", "Docker content");
        dockerDocument.setUser(userA);
        EmbeddingVector queryEmbedding = vector(1.0f);
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(documentRepository.findByUserAndContentContainingIgnoreCase(userA, "Docker"))
                .thenReturn(List.of(dockerDocument));
        when(aiClient.generateQueryEmbedding("Docker"))
                .thenReturn(queryEmbedding);
        when(vectorSearchRepository.findSimilar(eq(1L), same(queryEmbedding), eq(10)))
                .thenReturn(List.of());

        assertThat(documentService.searchDocuments("Docker", userA.getEmail()))
                .containsExactly(dockerDocument);
        assertThat(documentService.searchSemantically("Docker", 10, userA.getEmail()))
                .isEmpty();

        verify(documentRepository)
                .findByUserAndContentContainingIgnoreCase(userA, "Docker");
        verify(vectorSearchRepository).findSimilar(1L, queryEmbedding, 10);
    }

    private User user(Long id, String email) {
        User user = new User(email, "encoded-password", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private EmbeddingVector vector(float firstValue) {
        return new EmbeddingVector(IntStream.range(0, 768)
                .mapToObj(index -> index == 0 ? firstValue : 0.0f)
                .toList());
    }
}
