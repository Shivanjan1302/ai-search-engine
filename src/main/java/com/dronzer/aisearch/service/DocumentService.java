package com.dronzer.aisearch.service;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document saveDocument(String filename, String content) {

        Document document = new Document(filename, content);

        return documentRepository.save(document);
    }

    public List<Document> getAllDocuments() {

        return documentRepository.findAll();
    }

    public List<Document> searchDocuments(String keyword) {

        return documentRepository.findByContentContainingIgnoreCase(keyword);
    }
}