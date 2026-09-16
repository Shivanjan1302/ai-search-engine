package com.dronzer.aisearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateDocumentRequest {

    @NotBlank(message = "filename must not be blank")
    @Size(max = 255, message = "filename must not exceed 255 characters")
    private String filename;

    private String content;

    public CreateDocumentRequest() {
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}