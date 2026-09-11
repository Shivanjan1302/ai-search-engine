package com.dronzer.aisearch.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.dronzer.aisearch.entity.DocumentChunk;
import com.dronzer.aisearch.entity.User;

public interface DocumentChunkRepository
                extends Repository<DocumentChunk, Long> {

        <S extends DocumentChunk> S save(S chunk);

    List<DocumentChunk> findByDocumentIdAndDocumentUser(
            Long documentId,
            User user);

    List<DocumentChunk> findByDocumentUser(User user);
}
