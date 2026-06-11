package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

}