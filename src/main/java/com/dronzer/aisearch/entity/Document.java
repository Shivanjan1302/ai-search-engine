package com.dronzer.aisearch.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Document() {
    }

    public Document(
            String filename,
            String content) {

        this.filename = filename;
        this.content = content;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public User getUser() {
        return user;
    }

    public void setFilename(
            String filename) {

        this.filename = filename;
    }

    public void setContent(
            String content) {

        this.content = content;
    }

    public void setUser(
            User user) {

        this.user = user;
    }
}