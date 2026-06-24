package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.entity.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentEmbeddingRepository
        extends JpaRepository<DocumentEmbedding, Long> {
}