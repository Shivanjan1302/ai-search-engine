ALTER TABLE users
    ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS google_subject VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS users_google_subject_idx
    ON users(google_subject)
    WHERE google_subject IS NOT NULL;