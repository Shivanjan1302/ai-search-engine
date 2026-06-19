package com.dronzer.aisearch.service;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.DocumentRepository;
import com.dronzer.aisearch.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    private final UserRepository userRepository;

    private final JwtService jwtService;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            JwtService jwtService) {

        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public Document saveDocument(
            String filename,
            String content) {

        Document document =
                new Document(
                        filename,
                        content);

        return documentRepository.save(
                document);
    }

    public Document saveDocument(
            String filename,
            String content,
            String email) {

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

        return documentRepository.save(
                document);
    }

    public List<Document> getAllDocuments() {

        return documentRepository.findAll();
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

}