package com.dronzer.aisearch.dto;

/**
 * Raw hit produced by {@link com.dronzer.aisearch.repository.KeywordSearchRepository}.
 *
 * <p>This is an internal retrieval DTO and is <strong>not</strong> exposed through any
 * public RAG API. It exists only to carry the PostgreSQL full-text relevance score
 * alongside the chunk identity so the {@link SemanticSearchResult} merger/ranker can
 * combine it with vector search results.
 */
public record KeywordSearchResult(
        Long documentId,
        String filename,
        Integer chunkIndex,
        String chunkText,
        double keywordScore) {
}