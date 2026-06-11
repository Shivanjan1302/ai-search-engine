package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.CreateDocumentRequest;
import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.service.DocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public Document createDocument(
            @RequestBody CreateDocumentRequest request) {

        return documentService.saveDocument(
                request.getFilename(),
                request.getContent()
        );
    }

    @GetMapping
    public List<Document> getAllDocuments() {

        return documentService.getAllDocuments();
    }
}