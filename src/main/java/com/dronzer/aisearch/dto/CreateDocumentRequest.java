package com.dronzer.aisearch.dto;

public class CreateDocumentRequest {

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