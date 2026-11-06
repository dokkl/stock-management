# Data Model: 카카오 간편로그인과 다중 사용자 지원

**Feature**: `002-kakao-login-multiuser`

기능 001의 데이터 모델(`specs/001-stock-portfolio-ai-guide/data-model.md`)에서 바뀌는 부분만 적는다.

## 관계 변화

```text
(001)  owner (1) ──< account ──< holding ...     ai_credential (공급자당 1)     guide_request

(002)  app_user (1) ──< account (N) ──< holding (N) >── security ── quote      ← security·quote는 공유
            │  └────< ai_credential (N, 사용자·공급자당 1)
            └───────< guide_request (N)
       kakao_unlink_queue (탈퇴 후 연결 해제 재시도, 사용자와 FK 없음)
```

## app_user (신규, `owner` 대체)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, identity | |
| kakao_id | BIGINT | NULL, 부분 유니크(`WHERE kakao_id IS NOT NULL`) | 카카오 회원번호. 레거시 사용자만 NULL |
| display_name | VARCHAR(40) | NOT NULL | 카카오 닉네임(선택 동의). 없으면 "카카오 사용자" |
| legacy | BOOLEAN | NOT NULL, DEFAULT false | 기능 001 소유자 데이터를 담은 미인수 사용자 |
| selected_ai_provider | VARCHAR(20) | NULL, CHECK IN ('ANTHROPIC','OPENAI') | 가이드용 공급자(기존 owner 컬럼 이동) |
| created_at / last_login_at | TIMESTAMPTZ | NOT NULL / NULL | |

- **불변식**: `legacy = true`인 행은 최대 1개이고 `kakao_id IS NULL`이다(`CHECK (NOT legacy OR kakao_id IS NULL)`,
  부분 유니크 `WHERE legacy`).
- **가입/로그인 상태 전이**(R2, R6):

  ```text
  카카오 로그인(kakao_id=K)
    ├─ K 사용자 있음 → display_name·last_login_at 갱신
    ├─ 레거시 행 있음 → 레거시 행이 K를 차지(legacy=false), 기존 데이터 인수
    └─ 그 외 → 새 사용자 INSERT
  탈퇴 → 행 삭제(연쇄 삭제) → kakao_unlink_queue에 K 등록(즉시 해제 실패 시)
  ```

## account (변경)

| 변경 | 내용 |
|---|---|
| + user_id | BIGINT NOT NULL, FK → app_user `ON DELETE CASCADE` |
| 유니크 | `ux_account_name (name) WHERE deleted_at IS NULL` → `(user_id, name) WHERE deleted_at IS NULL` (FR-010) |
| 인덱스 | `ix_account_user (user_id) WHERE deleted_at IS NULL` |
| 상한 | 사용자당 삭제되지 않은 계좌 ≤ 2 (FR-013) |

## holding (변경 없음, 소유는 account 경유)

- 상한: 사용자 계좌들의 holding 합계 ≤ 20 (FR-013).
- 탈퇴 시 account 연쇄 삭제로 함께 삭제된다.

## ai_credential (변경)

| 변경 | 내용 |
|---|---|
| + user_id | BIGINT NOT NULL, FK → app_user `ON DELETE CASCADE` |
| 유니크 | `provider UNIQUE` → `(user_id, provider)` UNIQUE |

- 선택 공급자는 `app_user.selected_ai_provider`. 삭제 시 선택 정리 규칙(기능 001 FR-023c)은 사용자 안에서 동일.

## guide_request (변경)

| 변경 | 내용 |
|---|---|
| + user_id | BIGINT NOT NULL, FK → app_user `ON DELETE CASCADE` |
| 슬롯 유니크 | `(security_id, usage_date, slot) WHERE status <> 'FAILED'` → `(user_id, security_id, usage_date, slot) WHERE status <> 'FAILED'` (FR-014) |
| 기록 인덱스 | `(security_id, requested_at DESC) WHERE SUCCEEDED` → `(user_id, security_id, requested_at DESC) WHERE status = 'SUCCEEDED'` |
| 불변 트리거 | 그대로(UPDATE만 막고 DELETE는 허용 → 탈퇴 연쇄 삭제 가능) |

- `user_id`는 요청한 사용자다. account_id는 같은 사용자의 계좌다(애플리케이션이 보장).

## kakao_unlink_queue (신규, V10)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| kakao_id | BIGINT | PK | 연결 해제할 카카오 회원번호 |
| attempts | INT | NOT NULL, DEFAULT 0 | 시도 횟수(최대 10) |
| next_attempt_at | TIMESTAMPTZ | NOT NULL | 지수 백오프(1분, 2분, 4분 … 상한 6시간) |
| last_error | VARCHAR(100) | NULL | HTTP 상태 등 짧은 사유(응답 본문·키 미포함) |

- 탈퇴 직후 즉시 해제에 성공하면 행을 만들지 않는다. 같은 카카오 ID로 다시 가입해도 큐 항목은 그대로 처리한다
  (재가입 시 카카오가 새로 동의를 받는다).

## 공유 데이터 (변경 없음)

`security`, `quote`, `market_token`은 사용자와 무관하다. 시세 스케줄러의 "보유 종목" 조회는 모든 사용자의 holding
합집합(중복 제거)이다.

## 마이그레이션 순서 (V9, 한 트랜잭션)

1. `app_user` 생성
2. `owner` 행이 있으면 레거시 사용자 1행 INSERT(`selected_ai_provider` 승계, display_name '기존 사용자')
3. `account`·`ai_credential`·`guide_request`에 `user_id` 추가(NULL) → 레거시 사용자 ID로 채움 → NOT NULL·FK
4. 유니크·인덱스 교체(account 이름, ai_credential, 가이드 슬롯·기록)
5. `owner` 테이블과 `ck_owner_selected_ai_provider` 제거

## 요구사항 추적

| 요구사항 | 엔티티/규칙 |
|---|---|
| FR-001~007 | app_user, 세션(Spring Security) |
| FR-008~010 | account·ai_credential·guide_request.user_id, (user_id, name) 유니크 |
| FR-011 | security·quote 공유, 시세 대상 합집합 |
| FR-013~014 | 사용자별 계좌·보유 상한, 슬롯 유니크에 user_id |
| FR-015~016 | ON DELETE CASCADE, kakao_unlink_queue, kakao_id 재가입 |
| FR-017 | 레거시 사용자 행과 원자적 인수 |

## 변경 012: 공용 AI 키 (V11)

- `ai_credential`: `user_id` 컬럼·FK 제거, `provider` UNIQUE(공급자당 1개, 관리자 공용).
- `ai_settings`(신규, 항상 1행): `id BOOLEAN PK CHECK (id)`, `selected_provider VARCHAR(20) NULL`, `updated_at`.
  가이드용 공급자 선택. `app_user.selected_ai_provider`는 제거한다.
- V11은 Flyway placeholder `admin_kakao_ids`(= `ADMIN_KAKAO_IDS`)로 남길 키의 주인을 정한다. 지정이 없으면 `min(app_user.id)`.
