package com.dronzer.aisearch.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dronzer.aisearch.dto.CreateDocumentRequest;
import com.dronzer.aisearch.dto.DocumentResponse;
import com.dronzer.aisearch.dto.ReindexResponse;
import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.exception.DocumentProcessingException;
import com.dronzer.aisearch.exception.UnsupportedDocumentException;
import com.dronzer.aisearch.exception.UploadSizeExceededException;
import com.dronzer.aisearch.service.DocumentService;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

        @Value("${app.upload.max-file-size-bytes:10485760}")
        private long maxUploadBytes = 10485760L;

        @Value("${app.document.max-content-chars:5000000}")
        private int maxContentChars = 5000000;

        @Value("${app.document.max-pdf-pages:500}")
        private int maxPdfPages = 500;

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
                        @AuthenticationPrincipal String email) {

                if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("Uploaded file must not be empty");
                }
                if (file.getSize() > maxUploadBytes) {
                        throw new UploadSizeExceededException();
                }

                String filename = file.getOriginalFilename();
                if (filename == null || filename.isBlank()) {
                        throw new IllegalArgumentException("Uploaded file must have a filename");
                }

                String content;
                if (filename.toLowerCase().endsWith(".pdf")) {
                        try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
                                if (pdfDocument.getNumberOfPages() > maxPdfPages) {
                                        throw new UploadSizeExceededException();
                                }
                                content = new PDFTextStripper().getText(pdfDocument);
                        } catch (UploadSizeExceededException exception) {
                                throw exception;
                        } catch (IOException | RuntimeException exception) {
                                throw new DocumentProcessingException();
                        }
                } else {
                        String contentType = file.getContentType();
                        boolean textFile = filename.toLowerCase().endsWith(".txt")
                                        || (contentType != null && contentType.startsWith("text/"));
                        if (!textFile) {
                                throw new UnsupportedDocumentException();
                        }
                        try {
                                content = new String(file.getBytes(), StandardCharsets.UTF_8);
                        } catch (IOException exception) {
                                throw new DocumentProcessingException();
                        }
                }

                if (content.length() > maxContentChars) {
                        throw new UploadSizeExceededException();
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
