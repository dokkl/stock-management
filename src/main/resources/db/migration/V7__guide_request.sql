-- US3: 가이드 요청 = 하루 한도 슬롯 예약 + 기록 (FR-018~FR-022, FR-025~FR-028, research R7)
CREATE TABLE guide_request (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_id           BIGINT        NOT NULL REFERENCES security (id),
    holding_id            BIGINT        NULL REFERENCES holding (id) ON DELETE SET NULL,
    account_id            BIGINT        NOT NULL REFERENCES account (id),
    usage_date            DATE          NOT NULL,
    slot                  SMALLINT      NOT NULL CHECK (slot IN (1, 2)),
    status                VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    failure_kind          VARCHAR(20)   NULL
        CHECK (failure_kind IN ('CREDENTIAL_ERROR', 'TRANSIENT_ERROR', 'INVALID_STATE')),
    provider              VARCHAR(20)   NOT NULL CHECK (provider IN ('ANTHROPIC', 'OPENAI')),
    model                 VARCHAR(80)   NULL,
    account_name_snapshot VARCHAR(30)   NOT NULL,
    price_snapshot        NUMERIC(19,4) NOT NULL,
    avg_price_snapshot    NUMERIC(19,4) NOT NULL,
    quantity_snapshot     NUMERIC(20,6) NOT NULL,
    return_rate_snapshot  NUMERIC(9,4)  NOT NULL,
    currency              CHAR(3)       NOT NULL,
    content               JSONB         NULL,
    raw_text              TEXT          NULL,
    requested_at          TIMESTAMPTZ   NOT NULL,
    completed_at          TIMESTAMPTZ   NULL,
    CONSTRAINT ck_guide_failure_kind CHECK ((status = 'FAILED') = (failure_kind IS NOT NULL)),
    CONSTRAINT ck_guide_result CHECK (status <> 'SUCCEEDED' OR content IS NOT NULL OR raw_text IS NOT NULL)
);

-- 핵심: 실패하지 않은 요청만 종목·날짜별 슬롯 1, 2를 차지한다. 실패하면 인덱스에서 빠져 슬롯이 반환된다.
CREATE UNIQUE INDEX ux_guide_slot ON guide_request (security_id, usage_date, slot) WHERE status <> 'FAILED';
CREATE INDEX ix_guide_history ON guide_request (security_id, requested_at DESC) WHERE status = 'SUCCEEDED';
CREATE INDEX ix_guide_pending ON guide_request (requested_at) WHERE status = 'PENDING';

-- 종료 상태(SUCCEEDED/FAILED) 행은 바꿀 수 없다(FR-028).
-- 예외: 보유 종목 삭제 시 FK의 ON DELETE SET NULL이 holding_id만 비우는 것은 허용한다(FR-027 기록 유지).
CREATE FUNCTION guide_request_immutable() RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('SUCCEEDED', 'FAILED')
       AND (to_jsonb(NEW) - 'holding_id') IS DISTINCT FROM (to_jsonb(OLD) - 'holding_id') THEN
        RAISE EXCEPTION 'guide_request % is final (status=%)', OLD.id, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_guide_request_immutable
    BEFORE UPDATE ON guide_request
    FOR EACH ROW EXECUTE FUNCTION guide_request_immutable();
