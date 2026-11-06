-- US4: AI 공급자 키 (FR-023~FR-023c, research R5). 원문은 저장하지 않는다.
CREATE TABLE ai_credential (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider    VARCHAR(20)  NOT NULL UNIQUE CHECK (provider IN ('ANTHROPIC', 'OPENAI')),
    ciphertext  BYTEA        NOT NULL,
    iv          BYTEA        NOT NULL CHECK (octet_length(iv) = 12),
    key_version SMALLINT     NOT NULL,
    last4       CHAR(4)      NOT NULL,
    verified_at TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

ALTER TABLE owner
    ADD CONSTRAINT ck_owner_selected_ai_provider CHECK (selected_ai_provider IN ('ANTHROPIC', 'OPENAI'));
