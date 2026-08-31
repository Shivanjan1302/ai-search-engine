package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.model.EmbeddingVector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.StringJoiner;

@Repository
public class VectorSearchRepository {

    private static final int EMBEDDING_DIMENSIONS = 768;

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertEmbedding(Long chunkId, EmbeddingVector embedding) {
        jdbcTemplate.update("""
                INSERT INTO document_embeddings (chunk_id, embedding)
                VALUES (?, CAST(? AS vector))
                ON CONFLICT (chunk_id)
                DO UPDATE SET embedding = EXCLUDED.embedding
                """, chunkId, toVectorLiteral(embedding));
    }

    public List<SemanticSearchResult> findSimilar(
            Long userId,
            EmbeddingVector queryEmbedding,
            int limit) {

        return jdbcTemplate.query("""
                SELECT document_id, filename, chunk_index, chunk_text, 1 - distance AS similarity
                FROM (
                    SELECT d.id AS document_id,
                           d.filename,
                           c.chunk_index,
                           c.chunk_text,
                           e.embedding <=> CAST(? AS vector) AS distance
                    FROM document_embeddings e
                    JOIN document_chunks c ON c.id = e.chunk_id
                    JOIN documents d ON d.id = c.document_id
                    WHERE d.user_id = ?
                ) ranked_chunks
                ORDER BY distance
                LIMIT ?
                """, (resultSet, rowNumber) -> new SemanticSearchResult(
                resultSet.getLong("document_id"),
                resultSet.getString("filename"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getDouble("similarity")),
                toVectorLiteral(queryEmbedding), userId, limit);
    }

    private String toVectorLiteral(EmbeddingVector embedding) {
        if (embedding.size() != EMBEDDING_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Expected a " + EMBEDDING_DIMENSIONS + "-dimension embedding");
        }

        StringJoiner values = new StringJoiner(",", "[", "]");
        for (Float value : embedding.getValues()) {
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding values must be finite numbers");
            }
            values.add(Float.toString(value));
        }

        return values.toString();
    }
}
