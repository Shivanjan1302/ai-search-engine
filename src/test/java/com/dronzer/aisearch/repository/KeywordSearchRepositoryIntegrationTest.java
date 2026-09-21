package com.dronzer.aisearch.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.dronzer.aisearch.dto.KeywordSearchResult;

@EnabledIfEnvironmentVariable(named = "RUN_PGVECTOR_TESTS", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
class KeywordSearchRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("aisearch")
                    .withUsername("test")
                    .withPassword("test");

    private static JdbcTemplate jdbcTemplate;
    private static KeywordSearchRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE documents (id BIGINT PRIMARY KEY, filename VARCHAR(255), user_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE document_chunks (id BIGINT PRIMARY KEY, document_id BIGINT, chunk_index INTEGER, chunk_text TEXT)");
        jdbcTemplate.update("INSERT INTO users (id) VALUES (1), (2)");
        jdbcTemplate.update("INSERT INTO documents (id, filename, user_id) VALUES (10, 'runbook.txt', 1), (20, 'private.txt', 2)");
        jdbcTemplate.update("INSERT INTO document_chunks (id, document_id, chunk_index, chunk_text) VALUES (100, 10, 0, 'Deployment fails with ERR_DB_CONN_42X'), (200, 20, 0, 'Private ERR_DB_CONN_42X note')");
        repository = new KeywordSearchRepository(jdbcTemplate);
    }

    @Test
    void findsOwnedChunksAndNeverLeaksAnotherUsersChunks() {
        List<KeywordSearchResult> owned = repository.findMatches(1L, "ERR_DB_CONN_42X", 10);
        assertThat(owned).extracting(KeywordSearchResult::documentId).containsExactly(10L);
        List<KeywordSearchResult> other = repository.findMatches(2L, "Deployment", 10);
        assertThat(other).extracting(KeywordSearchResult::documentId).containsExactly(20L);
    }
}