ALTER TABLE notes
    ADD COLUMN user_id BIGINT;

ALTER TABLE notes
    ADD CONSTRAINT notes_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id);

-- Existing notes have no ownership provenance and remain inaccessible orphan rows.
ALTER TABLE notes
    ADD CONSTRAINT notes_user_id_required
    CHECK (user_id IS NOT NULL) NOT VALID;

CREATE INDEX notes_user_id_idx
    ON notes(user_id);
