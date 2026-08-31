package com.dronzer.aisearch.dto;

public record SemanticSearchResult(
        Long documentId,
        String filename,
        Integer chunkIndex,
        String chunkText,
        double similarity) {
}
