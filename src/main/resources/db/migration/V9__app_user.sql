-- 002: 카카오 로그인 + 다중 사용자 (data-model.md "마이그레이션 순서", research R4·R5·R6)
-- 기존 1인 소유자(owner) 데이터는 "레거시 사용자"(kakao_id 없음)로 옮겨 두고, 첫 카카오 로그인이 그 행을 차지한다.

CREATE TABLE app_user (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kakao_id             BIGINT       NULL,
    display_name         VARCHAR(40)  NOT NULL,
    legacy               BOOLEAN      NOT NULL DEFAULT false,
    selected_ai_provider VARCHAR(20)  NULL CHECK (selected_ai_provider IN ('ANTHROPIC', 'OPENAI')),
    created_at           TIMESTAMPTZ  NOT NULL,
    last_login_at        TIMESTAMPTZ  NULL,
    CONSTRAINT ck_app_user_legacy_unclaimed CHECK (NOT legacy OR kakao_id IS NULL)
);

CREATE UNIQUE INDEX ux_app_user_kakao ON app_user (kakao_id) WHERE kakao_id IS NOT NULL;
CREATE UNIQUE INDEX ux_app_user_legacy ON app_user (legacy) WHERE legacy;

-- 기존 소유자가 있으면 레거시 사용자 1행
INSERT INTO app_user (display_name, legacy, selected_ai_provider, created_at)
SELECT '기존 사용자', true, selected_ai_provider, created_at FROM owner WHERE id = 1;

-- 사용자 소유 루트 3곳에 user_id
ALTER TABLE account ADD COLUMN user_id BIGINT;
ALTER TABLE ai_credential ADD COLUMN user_id BIGINT;
ALTER TABLE guide_request ADD COLUMN user_id BIGINT;

-- guide_request는 종료 상태 행 UPDATE를 막는 트리거가 있으므로 이전 동안만 끈다
ALTER TABLE guide_request DISABLE TRIGGER tr_guide_request_immutable;
UPDATE account SET user_id = (SELECT id FROM app_user WHERE legacy);
UPDATE ai_credential SET user_id = (SELECT id FROM app_user WHERE legacy);
UPDATE guide_request SET user_id = (SELECT id FROM app_user WHERE legacy);
ALTER TABLE guide_request ENABLE TRIGGER tr_guide_request_immutable;

ALTER TABLE account ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
ALTER TABLE ai_credential ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_ai_credential_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
ALTER TABLE guide_request ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_guide_request_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;

-- 탈퇴(사용자 삭제) 시 계좌와 함께 보유 종목도 지운다(research R7)
ALTER TABLE holding DROP CONSTRAINT holding_account_id_fkey,
    ADD CONSTRAINT holding_account_id_fkey FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE;

-- 계좌 이름은 사용자 안에서 유일(FR-010)
DROP INDEX ux_account_name;
CREATE UNIQUE INDEX ux_account_user_name ON account (user_id, name) WHERE deleted_at IS NULL;
CREATE INDEX ix_account_user ON account (user_id) WHERE deleted_at IS NULL;

-- AI 키는 사용자·공급자당 1개
ALTER TABLE ai_credential DROP CONSTRAINT ai_credential_provider_key;
ALTER TABLE ai_credential ADD CONSTRAINT ux_ai_credential_user_provider UNIQUE (user_id, provider);

-- 하루 가이드 한도는 사용자·종목·날짜 단위(FR-014)
DROP INDEX ux_guide_slot;
CREATE UNIQUE INDEX ux_guide_slot ON guide_request (user_id, security_id, usage_date, slot) WHERE status <> 'FAILED';
DROP INDEX ix_guide_history;
CREATE INDEX ix_guide_history ON guide_request (user_id, security_id, requested_at DESC) WHERE status = 'SUCCEEDED';

-- 비밀번호 소유자 제거(FR-001)
DROP TABLE owner;
