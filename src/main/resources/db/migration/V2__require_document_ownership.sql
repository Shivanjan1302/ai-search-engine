ALTER TABLE documents
    ADD CONSTRAINT documents_user_id_required
    CHECK (user_id IS NOT NULL) NOT VALID;

ALTER TABLE document_chunks
    ADD CONSTRAINT document_chunks_document_id_required
    CHECK (document_id IS NOT NULL) NOT VALID;

ALTER TABLE document_embeddings
    ADD CONSTRAINT document_embeddings_chunk_id_required
    CHECK (chunk_id IS NOT NULL) NOT VALID;
