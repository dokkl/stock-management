-- Foundational: 소유자 1인 (FR-001, FR-001a)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE owner (
    id                   BIGINT       PRIMARY KEY CHECK (id = 1),
    password_hash        VARCHAR(255) NOT NULL,
    failed_attempts      INT          NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until         TIMESTAMPTZ  NULL,
    selected_ai_provider VARCHAR(20)  NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);
