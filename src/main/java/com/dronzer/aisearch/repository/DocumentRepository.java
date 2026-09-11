package com.dronzer.aisearch.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.dronzer.aisearch.entity.Document;
import com.dronzer.aisearch.entity.User;

public interface DocumentRepository extends Repository<Document, Long> {

    <S extends Document> S save(S document);

    List<Document> findByUserOrderByUploadedAtDesc(
            User user);

    List<Document> findByUserAndContentContainingIgnoreCase(
            User user,
            String keyword);

}
