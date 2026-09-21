-- Phase 2A: keyword/full-text retrieval.
--
-- Adds a PostgreSQL GIN index over the tsvector representation of each chunk's text so
-- that the full-text query used by KeywordSearchRepository is served by an index scan
-- rather than a sequential scan. The index covers only the chunk table; tenant
-- filtering (documents.user_id) reuses the pre-existing documents_user_id_idx.
--
-- This indexes an expression and stores no redundant data, so existing rows and
-- existing migrations are left untouched.
CREATE INDEX IF NOT EXISTS document_chunks_fts_idx
    ON document_chunks USING gin (to_tsvector('english', chunk_text));
