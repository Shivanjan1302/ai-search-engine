package com.dronzer.aisearch.repository;

import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dronzer.aisearch.dto.KeywordSearchResult;

/**
 * PostgreSQL-native full-text retrieval used by Phase 2A hybrid search.
 *
 * <p><strong>Tenant isolation is enforced in SQL, not in Java.</strong> Every query joins
 * {@code document_chunks} to {@code documents} and restricts results to
 * {@code d.user_id = ?}. A keyword search can therefore never return another user's
 * chunks even if the caller passes the wrong identifier, because the row is simply not
 * visible.
 *
 * <p>The index backing tenant filtering ({@code documents_user_id_idx}) and the
 * full-text GIN index ({@code document_chunks_fts_idx}, added in migration V5) make
 * this a bounded, indexed lookup rather than a full-table scan.
 *
 * <p>H2 (used by the unit test profile) does not implement the {@code tsvector} /
 * {@code ts_rank_cd} function set, so this repository is exercised against a real
 * PostgreSQL instance in the opt-in {@code KeywordSearchRepositoryIntegrationTest}.
 */
@Repository
public class KeywordSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public KeywordSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves the strongest keyword/full-text matches for {@code query} that are
     * owned by the user identified by {@code userId}.
     *
     * <p>The query uses {@code plainto_tsquery('english', ?)} against a precomputed
     * {@code to_tsvector('english', chunk_text)} expression (served by the GIN index)
     * and ranks results with {@code ts_rank_cd}. Ties on the text rank are broken by
     * {@code document_id, chunk_index} so the result order is deterministic.
     *
     * @param userId the authenticated user's id (never null)
     * @param query  the user question (never null); empty input matches nothing
     * @param limit  the maximum number of keyword hits to return
     * @return ranked keyword hits, empty when nothing matches
     */
    public List<KeywordSearchResult> findMatches(
            Long userId,
            String query,
            int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT d.id AS document_id,
                       d.filename,
                       c.chunk_index,
                       c.chunk_text,
                       ts_rank_cd(to_tsvector('english', c.chunk_text),
                                  plainto_tsquery('english', ?)) AS keyword_score
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.user_id = ?
                  AND to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', ?)
                ORDER BY keyword_score DESC, document_id ASC, chunk_index ASC
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new KeywordSearchResult(
                        resultSet.getLong("document_id"),
                        resultSet.getString("filename"),
                        resultSet.getInt("chunk_index"),
                        resultSet.getString("chunk_text"),
                        resultSet.getDouble("keyword_score")),
                query, userId, query, limit);
    }

    /** Tenant-scoped keyword retrieval with an optional trusted document constraint. */
    public List<KeywordSearchResult> findMatches(
            Long userId,
            String query,
            int limit,
            java.util.Set<Long> documentIds) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        if (documentIds == null || documentIds.isEmpty()) {
            return jdbcTemplate.query("""
                    SELECT d.id AS document_id,
                           d.filename,
                           c.chunk_index,
                           c.chunk_text,
                           ts_rank_cd(to_tsvector('english', c.chunk_text),
                                      plainto_tsquery('english', ?)) AS keyword_score
                    FROM document_chunks c
                    JOIN documents d ON d.id = c.document_id
                    WHERE d.user_id = ?
                      AND to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', ?)
                    ORDER BY keyword_score DESC, document_id ASC, chunk_index ASC
                    LIMIT ?
                    """,
                    (resultSet, rowNumber) -> new KeywordSearchResult(
                            resultSet.getLong("document_id"),
                            resultSet.getString("filename"),
                            resultSet.getInt("chunk_index"),
                            resultSet.getString("chunk_text"),
                            resultSet.getDouble("keyword_score")),
                    query, userId, query, limit);
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(documentIds.size(), "?"));
        String documentFilter = " AND d.id IN (" + placeholders + ")";
        java.util.List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(query);
        arguments.add(userId);
        arguments.add(query);
        arguments.addAll(documentIds);
        arguments.add(limit);
        return jdbcTemplate.query("""
                SELECT d.id AS document_id,
                       d.filename,
                       c.chunk_index,
                       c.chunk_text,
                       ts_rank_cd(to_tsvector('english', c.chunk_text),
                                  plainto_tsquery('english', ?)) AS keyword_score
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.user_id = ?
                  AND to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', ?)
                """ + documentFilter + """
                ORDER BY keyword_score DESC, document_id ASC, chunk_index ASC
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new KeywordSearchResult(
                        resultSet.getLong("document_id"),
                        resultSet.getString("filename"),
                        resultSet.getInt("chunk_index"),
                        resultSet.getString("chunk_text"),
                        resultSet.getDouble("keyword_score")),
                arguments.toArray());
    }
}
