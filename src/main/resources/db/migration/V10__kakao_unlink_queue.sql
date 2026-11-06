-- 002 US4: 탈퇴 후 카카오 연결 해제 재시도 대기열(research R7). 사용자 행은 이미 삭제되었으므로 FK가 없다.
CREATE TABLE kakao_unlink_queue (
    kakao_id        BIGINT       PRIMARY KEY,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      VARCHAR(100) NULL
);

CREATE INDEX ix_kakao_unlink_due ON kakao_unlink_queue (next_attempt_at) WHERE attempts < 10;
