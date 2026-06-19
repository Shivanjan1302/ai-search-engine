package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.CreateDocumentRequest;
import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.service.DocumentService;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.dronzer.aisearch.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final JwtService jwtService;

    public DocumentController(
            DocumentService documentService,
            JwtService jwtService) {

        this.documentService = documentService;
        this.jwtService = jwtService;
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

    @GetMapping("/search")
    public List<Document> searchDocuments(
            @RequestParam String keyword,
            HttpServletRequest request) {

        String token =
                jwtService.extractTokenFromRequest(
                        request);

        String email =
                jwtService.extractEmail(token);

        return documentService.searchDocuments(
                keyword,
                email);
    }

    @PostMapping("/upload")
    public Document uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request)
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

        String token =
                jwtService.extractTokenFromRequest(
                        request);

        String email =
                jwtService.extractEmail(token);

        return documentService.saveDocument(
                filename,
                content,
                email);
    }
}