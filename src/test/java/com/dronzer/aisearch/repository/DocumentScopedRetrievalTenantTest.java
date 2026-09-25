package com.dronzer.aisearch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.dronzer.aisearch.model.EmbeddingVector;

/**
 * Tenant-scoping regression coverage for the optional document-ID constraint.
 *
 * <p>These assertions are Docker-free and database-free: a recording
 * {@link JdbcTemplate} captures the exact SQL and bound arguments that the
 * repositories issue. They lock in the invariant that a caller-supplied
 * document-ID set is <em>additive only</em> — the mandatory
 * {@code d.user_id = ?} predicate and its bound argument must survive whenever a
 * document constraint is supplied, so document IDs can never widen retrieval
 * beyond the authenticated tenant.</p>
 */
class DocumentScopedRetrievalTenantTest {

    private RecordingJdbcTemplate jdbc;
    private VectorSearchRepository vectorRepository;
    private KeywordSearchRepository keywordRepository;

    @BeforeEach
    void setUp() {
        jdbc = new RecordingJdbcTemplate();
        vectorRepository = new VectorSearchRepository(jdbc);
        keywordRepository = new KeywordSearchRepository(jdbc);
    }

    @Test
    void vectorRetrievalKeepsTheTenantPredicateWhenDocumentIdsAreSupplied() {
        vectorRepository.findSimilar(1L, vector(1.0f, 0.0f), 10, Set.of(10L, 11L));

        assertThat(jdbc.lastSql())
                .contains("d.user_id = ?")
                .contains("d.id IN (?, ?)");
        assertThat(jdbc.lastArguments()).contains(1L, 10L, 11L);
    }

    @Test
    void vectorRetrievalWithoutDocumentIdsIsStillTenantScoped() {
        vectorRepository.findSimilar(1L, vector(1.0f, 0.0f), 10, Set.of());

        assertThat(jdbc.lastSql()).contains("d.user_id = ?").doesNotContain("d.id IN (");
        assertThat(jdbc.lastArguments()).contains(1L);
    }

    @Test
    void vectorRetrievalTreatsNullDocumentIdsAsNoConstraint() {
        vectorRepository.findSimilar(1L, vector(1.0f, 0.0f), 10, null);

        assertThat(jdbc.lastSql()).contains("d.user_id = ?").doesNotContain("d.id IN (");
    }

    @Test
    void keywordRetrievalKeepsTheTenantPredicateWhenDocumentIdsAreSupplied() {
        keywordRepository.findMatches(1L, "termination", 10, Set.of(10L, 11L));

        assertThat(jdbc.lastSql())
                .contains("d.user_id = ?")
                .contains("d.id IN (?, ?)");
        assertThat(jdbc.lastArguments()).contains(1L, 10L, 11L);
    }

    @Test
    void keywordRetrievalWithoutDocumentIdsIsStillTenantScoped() {
        keywordRepository.findMatches(1L, "termination", 10, Set.of());

        assertThat(jdbc.lastSql()).contains("d.user_id = ?").doesNotContain("d.id IN (");
        assertThat(jdbc.lastArguments()).contains(1L);
    }

    @Test
    void keywordRetrievalRejectsBlankQueryWithoutIssuingAnySql() {
        assertThat(keywordRepository.findMatches(1L, "   ", 10, Set.of(10L))).isEmpty();
        assertThat(keywordRepository.findMatches(1L, "q", 0, Set.of(10L))).isEmpty();

        assertThat(jdbc.statements).isEmpty();
    }

    @Test
    void embeddingUpsertKeepsTheOwnershipPredicateInSql() {
        vectorRepository.upsertEmbedding(200L, 1L, vector(0.0f, 1.0f));

        assertThat(jdbc.lastSql()).contains("d.user_id = ?");
        assertThat(jdbc.lastUpdateArguments()).contains(200L, 1L);
    }

    @Test
    void embeddingUpsertRejectsAMalformedEmbeddingBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> vectorRepository.upsertEmbedding(
                200L, 1L, new EmbeddingVector(List.of(1.0f, 2.0f))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("768");
        assertThat(jdbc.statements).isEmpty();
    }

    private static EmbeddingVector vector(float first, float second) {
        List<Float> values = new ArrayList<>(768);
        values.add(first);
        values.add(second);
        for (int index = 2; index < 768; index++) {
            values.add(0.0f);
        }
        return new EmbeddingVector(values);
    }

    /** Captures SQL text and bound arguments without opening a real connection. */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final List<String> statements = new ArrayList<>();
        private final List<Object[]> arguments = new ArrayList<>();
        private final List<Object[]> updateArguments = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            statements.add(sql);
            arguments.add(args);
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            statements.add(sql);
            updateArguments.add(args);
            return 1;
        }

        String lastSql() {
            return statements.get(statements.size() - 1);
        }

        List<Object> lastArguments() {
            return List.of(arguments.get(arguments.size() - 1));
        }

        List<Object> lastUpdateArguments() {
            return List.of(updateArguments.get(updateArguments.size() - 1));
        }
    }
}
