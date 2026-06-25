package com.dronzer.aisearch.dto.gemini;

public class EmbeddingRequest {

    private Content content;

    public EmbeddingRequest() {
    }

    public EmbeddingRequest(Content content) {
        this.content = content;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }
}