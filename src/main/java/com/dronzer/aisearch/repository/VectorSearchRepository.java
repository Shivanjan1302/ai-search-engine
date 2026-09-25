package com.dronzer.aisearch.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.model.EmbeddingVector;

@Repository
public class VectorSearchRepository {

    private static final int EMBEDDING_DIMENSIONS = 768;

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

        public int upsertEmbedding(
                        Long chunkId,
                        Long userId,
                        EmbeddingVector embedding) {

                return jdbcTemplate.update("""
                                INSERT INTO document_embeddings (chunk_id, embedding)
                                SELECT c.id, CAST(? AS vector)
                                FROM document_chunks c
                                JOIN documents d ON d.id = c.document_id
                                WHERE c.id = ?
                                    AND d.user_id = ?
                                ON CONFLICT (chunk_id)
                                DO UPDATE SET embedding = EXCLUDED.embedding
                                """, toVectorLiteral(embedding), chunkId, userId);
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
                ORDER BY distance, document_id, chunk_index
                LIMIT ?
                """, (resultSet, rowNumber) -> new SemanticSearchResult(
                resultSet.getLong("document_id"),
                resultSet.getString("filename"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getDouble("similarity")),
                toVectorLiteral(queryEmbedding), userId, limit);
    }

    /**
     * Tenant-scoped vector retrieval with an optional trusted document constraint.
     * The document IDs are additive filters only; {@code d.user_id = ?} remains mandatory.
     */
    public List<SemanticSearchResult> findSimilar(
            Long userId,
            EmbeddingVector queryEmbedding,
            int limit,
            Set<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return findSimilar(userId, queryEmbedding, limit);
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(documentIds.size(), "?"));
        String documentFilter = " AND d.id IN (" + placeholders + ")";
        List<Object> arguments = new ArrayList<>();
        arguments.add(toVectorLiteral(queryEmbedding));
        arguments.add(userId);
        arguments.addAll(documentIds);
        arguments.add(limit);
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
                """ + documentFilter + """
                ) ranked_chunks
                ORDER BY distance, document_id, chunk_index
                LIMIT ?
                """, (resultSet, rowNumber) -> new SemanticSearchResult(
                resultSet.getLong("document_id"),
                resultSet.getString("filename"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getDouble("similarity")),
                arguments.toArray());
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
