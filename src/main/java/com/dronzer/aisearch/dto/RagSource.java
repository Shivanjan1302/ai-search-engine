package com.dronzer.aisearch.dto;

public record RagSource(
        Long documentId,
        String filename,
        Integer chunkIndex,
        double similarity
) {
}
