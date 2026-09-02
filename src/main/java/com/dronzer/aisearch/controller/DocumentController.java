package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.CreateDocumentRequest;
import com.dronzer.aisearch.dto.DocumentResponse;
import com.dronzer.aisearch.dto.ReindexResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.service.DocumentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(
            DocumentService documentService) {

        this.documentService = documentService;
    }

    @PostMapping
    public DocumentResponse createDocument(
            @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal String email) {

        Document document =
                documentService.saveDocument(
                        request.getFilename(),
                        request.getContent(),
                        email);

        return documentService.toResponse(document);
    }

    @GetMapping
    public List<DocumentResponse> getDocuments(
            @AuthenticationPrincipal String email) {

        return documentService
                .getDocuments(email)
                .stream()
                .map(documentService::toResponse)
                .toList();
    }

    @GetMapping("/search")
    public List<DocumentResponse> searchDocuments(
            @RequestParam String keyword,
            @AuthenticationPrincipal String email) {

        return documentService
                .searchDocuments(
                        keyword,
                        email)
                .stream()
                .map(documentService::toResponse)
                .toList();
    }

    @GetMapping("/semantic-search")
    public List<SemanticSearchResult> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal String email) {

        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        int boundedLimit = Math.min(Math.max(limit, 1), 20);
        return documentService.searchSemantically(
                query,
                boundedLimit,
                email);
    }

    @PostMapping("/reindex")
    public ReindexResponse reindexDocuments(
            @AuthenticationPrincipal String email) {
        int chunkCount = documentService.reindexDocuments(email);
        return new ReindexResponse(chunkCount);
    }

    @PostMapping("/upload")
    public DocumentResponse uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String email)
            throws IOException {

        String filename = file.getOriginalFilename();

        String content;

        if (filename != null &&
                filename.toLowerCase().endsWith(".pdf")) {

            try (PDDocument pdfDocument =
                         Loader.loadPDF(file.getBytes())) {

                PDFTextStripper stripper =
                        new PDFTextStripper();

                content =
                        stripper.getText(pdfDocument);
            }

        } else {

            content = new String(
                    file.getBytes(),
                    StandardCharsets.UTF_8);
        }

        Document document =
                documentService.saveDocument(
                        filename,
                        content,
                        email);

        return documentService.toResponse(
                document);
    }
}
