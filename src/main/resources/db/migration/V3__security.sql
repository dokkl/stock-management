-- US1: 종목 마스터 (FR-004)
CREATE TABLE security (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market         VARCHAR(2)   NOT NULL CHECK (market IN ('KR', 'US')),
    exchange       VARCHAR(10)  NOT NULL CHECK (exchange IN ('KOSPI', 'KOSDAQ', 'NYSE', 'NASDAQ', 'AMEX')),
    symbol         VARCHAR(12)  NOT NULL,
    name_ko        VARCHAR(100) NULL,
    name_en        VARCHAR(150) NULL,
    currency       CHAR(3)      NOT NULL,
    trading_status VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (trading_status IN ('ACTIVE', 'SUSPENDED', 'DELISTED')),
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_security_exchange_symbol UNIQUE (exchange, symbol),
    CONSTRAINT ck_security_market_currency CHECK ((market = 'KR') = (currency = 'KRW')),
    CONSTRAINT ck_security_has_name CHECK (name_ko IS NOT NULL OR name_en IS NOT NULL)
);

CREATE INDEX ix_security_name_ko_trgm ON security USING gin (name_ko gin_trgm_ops);
CREATE INDEX ix_security_name_en_trgm ON security USING gin (name_en gin_trgm_ops);
CREATE INDEX ix_security_symbol_prefix ON security (upper(symbol) text_pattern_ops);
