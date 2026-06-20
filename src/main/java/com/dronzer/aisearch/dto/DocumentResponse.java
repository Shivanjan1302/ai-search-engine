package com.dronzer.aisearch.dto;

import java.time.LocalDateTime;

public class DocumentResponse {

    private Long id;

    private String filename;

    private LocalDateTime uploadedAt;

    public DocumentResponse(
            Long id,
            String filename,
            LocalDateTime uploadedAt) {

        this.id = id;
        this.filename = filename;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}