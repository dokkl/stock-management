-- Foundational: 시세 제공자(토스증권) 접근 토큰 캐시 (research R2). 토큰은 SecretCipher(AES-256-GCM)로 암호화한다.
CREATE TABLE market_token (
    provider    VARCHAR(10) PRIMARY KEY,
    ciphertext  BYTEA       NOT NULL,
    iv          BYTEA       NOT NULL CHECK (octet_length(iv) = 12),
    key_version SMALLINT    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL
);
