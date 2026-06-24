package com.dronzer.aisearch.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "document_embeddings")
public class DocumentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "chunk_id")
    private DocumentChunk chunk;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    public DocumentEmbedding() {
    }

    public Long getId() {
        return id;
    }

    public DocumentChunk getChunk() {
        return chunk;
    }

    public void setChunk(DocumentChunk chunk) {
        this.chunk = chunk;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }
}