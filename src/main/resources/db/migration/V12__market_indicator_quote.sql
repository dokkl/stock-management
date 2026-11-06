-- 003: 대시보드 시장 지표. 지표별 최신 1행(공유 데이터, 사용자 범위 없음).
CREATE TABLE market_indicator_quote (
    indicator      VARCHAR(16)   PRIMARY KEY
        CHECK (indicator IN ('KOSPI', 'KOSDAQ', 'USD_KRW', 'NASDAQ', 'SP500', 'DOW', 'WTI', 'VIX')),
    value          NUMERIC(20,4) NOT NULL CHECK (value > 0),
    previous_close NUMERIC(20,4) NULL,
    as_of          TIMESTAMPTZ   NOT NULL,
    as_of_date     DATE          NOT NULL,
    fetched_at     TIMESTAMPTZ   NOT NULL
);
