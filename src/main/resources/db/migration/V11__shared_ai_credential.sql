-- 012: AI 키를 관리자 공용 설정으로 바꾼다. 모든 사용자가 관리자가 등록한 키로 가이드를 받는다.
-- 남길 키의 주인: 관리자(ADMIN_KAKAO_IDS, Flyway placeholder admin_kakao_ids).
-- 관리자 지정이 없으면 가장 먼저 만들어진 사용자(기능 001의 기존 운영자)다.

-- 공용 AI 설정(항상 1행): 가이드에 쓸 공급자
CREATE TABLE ai_settings (
    id                BOOLEAN     PRIMARY KEY DEFAULT true CHECK (id),
    selected_provider VARCHAR(20) NULL CHECK (selected_provider IN ('ANTHROPIC', 'OPENAI')),
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE TEMPORARY TABLE shared_key_owner ON COMMIT DROP AS
SELECT id FROM app_user
WHERE kakao_id = ANY (string_to_array(nullif(trim('${admin_kakao_ids}'), ''), ',')::bigint[])
   OR (nullif(trim('${admin_kakao_ids}'), '') IS NULL AND id = (SELECT min(id) FROM app_user));

-- 관리자가 아닌 사용자의 키, 같은 공급자의 오래된 관리자 키를 지운다
DELETE FROM ai_credential c
WHERE c.user_id NOT IN (SELECT id FROM shared_key_owner)
   OR EXISTS (SELECT 1 FROM ai_credential d
              WHERE d.provider = c.provider AND d.user_id IN (SELECT id FROM shared_key_owner)
                AND (d.verified_at > c.verified_at OR (d.verified_at = c.verified_at AND d.id > c.id)));

-- 선택 공급자: 먼저 만들어진 관리자의 선택 중 키가 남은 것, 없으면 남은 키의 공급자
INSERT INTO ai_settings (selected_provider, updated_at)
SELECT coalesce(
           (SELECT u.selected_ai_provider FROM app_user u
            WHERE u.id IN (SELECT id FROM shared_key_owner)
              AND u.selected_ai_provider IN (SELECT provider FROM ai_credential)
            ORDER BY u.id LIMIT 1),
           (SELECT provider FROM ai_credential ORDER BY id LIMIT 1)),
       now();

ALTER TABLE ai_credential DROP CONSTRAINT ux_ai_credential_user_provider,
    DROP CONSTRAINT fk_ai_credential_user,
    DROP COLUMN user_id,
    ADD CONSTRAINT ux_ai_credential_provider UNIQUE (provider);

ALTER TABLE app_user DROP COLUMN selected_ai_provider;
