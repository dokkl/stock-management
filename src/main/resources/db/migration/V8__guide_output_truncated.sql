-- 가이드 실패 사유에 OUTPUT_TRUNCATED(AI 응답이 출력 길이 한도에서 잘림) 추가
ALTER TABLE guide_request DROP CONSTRAINT guide_request_failure_kind_check;
ALTER TABLE guide_request ADD CONSTRAINT guide_request_failure_kind_check
    CHECK (failure_kind IN ('CREDENTIAL_ERROR', 'TRANSIENT_ERROR', 'INVALID_STATE', 'OUTPUT_TRUNCATED'));
