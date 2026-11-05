-- US2: 종목별 최신 시세 (FR-009~FR-014, research R3)
-- session_date: 시세가 속한 거래일. prev_close는 그 직전 거래일 종가이며 거래일이 바뀔 때만 다시 받는다.
CREATE TABLE quote (
    security_id  BIGINT        PRIMARY KEY REFERENCES security (id),
    price        NUMERIC(19,4) NOT NULL,
    prev_close   NUMERIC(19,4) NOT NULL,
    session_date DATE          NOT NULL,
    market_state VARCHAR(12)   NOT NULL CHECK (market_state IN ('PRE_OPEN', 'OPEN', 'CLOSED', 'HOLIDAY')),
    as_of        TIMESTAMPTZ   NOT NULL,
    fetched_at   TIMESTAMPTZ   NOT NULL
);
