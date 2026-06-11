package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByContentContainingIgnoreCase(String keyword);

}