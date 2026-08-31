package com.dronzer.aisearch.client;

import com.dronzer.aisearch.model.EmbeddingVector;

public interface AIClient {

    EmbeddingVector generateDocumentEmbedding(String text);

    EmbeddingVector generateQueryEmbedding(String text);

    String generateAnswer(String prompt);

}
