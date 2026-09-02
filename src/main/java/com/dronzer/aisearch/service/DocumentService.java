package com.dronzer.aisearch.service;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.DocumentRepository;
import com.dronzer.aisearch.repository.DocumentChunkRepository;
import com.dronzer.aisearch.repository.UserRepository;
import com.dronzer.aisearch.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;
import com.dronzer.aisearch.dto.DocumentResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.client.AIClient;
import com.dronzer.aisearch.model.EmbeddingVector;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    private final UserRepository userRepository;

    private final ChunkService chunkService;

    private final DocumentChunkRepository chunkRepository;

    private final EmbeddingService embeddingService;

    private final AIClient aiClient;

    private final VectorSearchRepository vectorSearchRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            ChunkService chunkService,
            DocumentChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            AIClient aiClient,
            VectorSearchRepository vectorSearchRepository){

        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.chunkService = chunkService;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.aiClient = aiClient;
        this.vectorSearchRepository = vectorSearchRepository;
    }

    @Transactional
    public Document saveDocument(
            String filename,
            String content,
            String email) {

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Document content must not be blank");
        }

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"));

        Document document =
                new Document(
                        filename,
                        content);

        document.setUser(user);

        Document savedDocument =
                documentRepository.save(
                        document);

        chunkService.createChunks(
                savedDocument);

        return savedDocument;
    }

    public List<Document> getDocuments(String email) {
        User user = findUserByEmail(email);
        return documentRepository.findByUserOrderByUploadedAtDesc(user);
    }

    public List<Document> searchDocuments(
            String keyword,
            String email) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"));

        return documentRepository
                .findByUserAndContentContainingIgnoreCase(
                        user,
                        keyword);
    }

    public List<SemanticSearchResult> searchSemantically(
            String query,
            int limit,
            String email) {

        User user = findUserByEmail(email);
        EmbeddingVector queryEmbedding = aiClient.generateQueryEmbedding(query);

        return vectorSearchRepository.findSimilar(
                user.getId(),
                queryEmbedding,
                limit);
    }

    @Transactional
    public int reindexDocuments(String email) {
        User user = findUserByEmail(email);
        List<com.dronzer.aisearch.entity.DocumentChunk> chunks =
                chunkRepository.findByDocumentUser(user);

        for (com.dronzer.aisearch.entity.DocumentChunk chunk : chunks) {
            embeddingService.createEmbedding(chunk);
        }

        return chunks.size();
    }

    public DocumentResponse toResponse(
            Document document) {

        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getUploadedAt()
        );
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
