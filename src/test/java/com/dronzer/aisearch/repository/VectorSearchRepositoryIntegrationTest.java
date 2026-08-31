package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.dto.SemanticSearchResult;
import com.dronzer.aisearch.model.EmbeddingVector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_PGVECTOR_TESTS", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
class VectorSearchRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static JdbcTemplate jdbcTemplate;

    private static VectorSearchRepository vectorSearchRepository;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE documents (id BIGINT PRIMARY KEY, filename VARCHAR(255), user_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE document_chunks (id BIGINT PRIMARY KEY, document_id BIGINT, chunk_index INTEGER, chunk_text TEXT)");
        jdbcTemplate.execute("CREATE TABLE document_embeddings (chunk_id BIGINT PRIMARY KEY, embedding vector(768))");

        jdbcTemplate.update("INSERT INTO users (id) VALUES (1), (2)");
        jdbcTemplate.update("INSERT INTO documents (id, filename, user_id) VALUES (10, 'plans.txt', 1), (20, 'private.txt', 2)");
        jdbcTemplate.update("INSERT INTO document_chunks (id, document_id, chunk_index, chunk_text) VALUES (100, 10, 0, 'project plans'), (200, 20, 0, 'private project plans')");

        vectorSearchRepository = new VectorSearchRepository(jdbcTemplate);
    }

    @Test
    void ranksRelevantChunksAndExcludesOtherUsersData() {
        vectorSearchRepository.upsertEmbedding(100L, vector(1.0f, 0.0f));
        vectorSearchRepository.upsertEmbedding(200L, vector(1.0f, 0.0f));

        List<SemanticSearchResult> results = vectorSearchRepository.findSimilar(
                1L,
                vector(0.9f, 0.1f),
                10);

        assertThat(results)
                .extracting(SemanticSearchResult::documentId)
                .containsExactly(10L);
        assertThat(results.get(0).similarity()).isGreaterThan(0.9);
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
}
