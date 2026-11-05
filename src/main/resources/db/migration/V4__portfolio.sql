-- US1: 계좌와 보유 종목 (FR-002, FR-003, FR-006, FR-007)
CREATE TABLE account (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(30) NOT NULL,
    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_account_name ON account (name) WHERE deleted_at IS NULL;

CREATE TABLE holding (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT        NOT NULL REFERENCES account (id),
    security_id BIGINT        NOT NULL REFERENCES security (id),
    avg_price   NUMERIC(19,4) NOT NULL CHECK (avg_price > 0),
    quantity    NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ux_holding_account_security UNIQUE (account_id, security_id)
);

CREATE INDEX ix_holding_security ON holding (security_id);
