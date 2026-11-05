# Data Model: 계좌별 주식 보유 관리와 AI 투자 가이드

**Feature**: `001-stock-portfolio-ai-guide` | **Date**: 2026-11-05

이 문서는 PostgreSQL 스키마 관점의 엔티티 정의다. 모든 시각은 `TIMESTAMPTZ`(UTC 저장)다.
"날짜"의 업무 기준은 `Asia/Seoul`이다. 금액은 `NUMERIC(19,4)`, 수량은 `NUMERIC(20,6)`이다.

## ER 개요

```text
owner (1) ──< account (N) ──< holding (N) >── (1) security ──(1) quote
                                │                    │
                                │                    └──< guide_request (N)
                                └──────────────< guide_request (N, nullable FK)
owner (1) ──< ai_credential (N, provider당 1)
market_token (독립, 토스증권 토큰 캐시)
```

---

## owner

소유자 1인. 행은 최대 1개다.

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| password_hash | VARCHAR(255) | NOT NULL | Argon2id 해시 |
| failed_attempts | INT | NOT NULL, DEFAULT 0, ≥ 0 | 연속 로그인 실패 횟수 |
| locked_until | TIMESTAMPTZ | NULL | 잠금 해제 시각 |
| selected_ai_provider | VARCHAR(20) | NULL, `ai_credential`에 존재해야 함 | 가이드용으로 선택한 공급자 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | |

- **규칙**: 두 번째 행 생성은 막는다(`CHECK (id = 1)`). `/setup`은 행이 없을 때만 허용한다.
- **로그인 상태 전이**: 정상 → (실패 1~4) → 실패 5회째: `locked_until = now + 15m`, 카운터는 0으로
  초기화 → 잠금 해제 시각 경과 → 정상. 로그인에 성공하면 카운터를 0으로 만든다.

## account (계좌)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| name | VARCHAR(30) | NOT NULL, 1~30자(앞뒤 공백 제거 후), UNIQUE | 계좌명 (FR-002) |
| deleted_at | TIMESTAMPTZ | NULL | 소프트 삭제 시각 (FR-027 "삭제된 계좌" 표시용) |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | |

- **이름 유니크**: 삭제되지 않은 계좌끼리만 적용한다(`UNIQUE (name) WHERE deleted_at IS NULL`).
- **삭제**: `deleted_at`을 설정하고, 그 계좌의 holding을 모두 물리 삭제한다. 가이드 기록은 계좌명을
  스냅샷으로 들고 있으므로 계속 조회할 수 있다.

## security (종목 마스터)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| market | VARCHAR(2) | NOT NULL, `KR` \| `US` | |
| exchange | VARCHAR(10) | NOT NULL, `KOSPI` \| `KOSDAQ` \| `NYSE` \| `NASDAQ` \| `AMEX` | |
| symbol | VARCHAR(12) | NOT NULL | 종목코드(6자리) 또는 티커 |
| name_ko | VARCHAR(100) | NULL | 한글명 (국내 필수, 해외 선택) |
| name_en | VARCHAR(150) | NULL | 영문명 |
| currency | CHAR(3) | NOT NULL, `KRW` \| `USD` | market에서 결정됨 |
| trading_status | VARCHAR(12) | NOT NULL, DEFAULT `ACTIVE` | `ACTIVE` \| `SUSPENDED` \| `DELISTED` |
| updated_at | TIMESTAMPTZ | NOT NULL | 마스터 동기화 시각 |

- **유니크**: `(exchange, symbol)`
- **검색 인덱스**: `pg_trgm` GIN 인덱스(`name_ko`, `name_en`)와 `symbol`의 prefix 인덱스(FR-004 검색).
- **불변식**: `market = KR` ⇔ `currency = KRW`
- **마스터 동기화**: 사라진 종목은 삭제하지 않고 `DELISTED`로 표시한다(Edge Case).

## holding (보유 종목)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| account_id | BIGINT | FK → account, NOT NULL | |
| security_id | BIGINT | FK → security, NOT NULL | |
| avg_price | NUMERIC(19,4) | NOT NULL, > 0 | 평단가, security.currency 기준 |
| quantity | NUMERIC(20,6) | NOT NULL, > 0 | 보유 수량 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | |

- **유니크**: `(account_id, security_id)` (FR-006)
- **검증**(도메인 + DB CHECK):
  - KRW 평단가는 소수 자릿수 0이다.
  - USD 평단가는 소수 자릿수 4 이하다.
  - KR 종목 수량은 정수이고, US 종목 수량은 소수 6자리까지 허용한다.
  - 둘 다 0보다 커야 한다(FR-007).
- **파생값**(저장하지 않고 조회 시 계산하는 도메인 로직):
  - `costBasis = avg_price × quantity`
  - `marketValue = quote.price × quantity`
  - `unrealizedPnl = marketValue − costBasis`
  - `returnRate = unrealizedPnl / costBasis`, 소수 넷째 자리 반올림(HALF_UP)
  - `direction = sign(unrealizedPnl)`, 값은 `UP` \| `DOWN` \| `FLAT`
- **상한**: 소유자 전체 holding 행 수 ≤ 500 (FR-003a, 애플리케이션에서 등록 시 검증)

## quote (시세)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| security_id | BIGINT | PK, FK → security | 종목당 최신 1행 |
| price | NUMERIC(19,4) | NOT NULL | 현재가(장 마감 후에는 종가) |
| prev_close | NUMERIC(19,4) | NOT NULL | 전일 종가(session_date 직전 거래일 종가) |
| session_date | DATE | NOT NULL | 시세가 속한 거래일. 장 시작 전·휴장일에는 직전 영업일. 바뀔 때만 전일 종가를 다시 받는다 |
| market_state | VARCHAR(12) | NOT NULL | `PRE_OPEN` \| `OPEN` \| `CLOSED` \| `HOLIDAY` |
| as_of | TIMESTAMPTZ | NOT NULL | 제공자가 알려준 시세 기준 시각 |
| fetched_at | TIMESTAMPTZ | NOT NULL | 마지막으로 성공적으로 받아온 시각 |

- **파생값**:
  - `change = price − prev_close`
  - `changeRate = change / prev_close`
  - `direction = UP`(change > 0) \| `DOWN`(< 0) \| `FLAT`(= 0) (FR-010)
- **신선도**: `now − fetched_at > 1분`이고 시장이 `OPEN`이면 `STALE`로 표시한다. 시세 행이 없으면
  `UNAVAILABLE`로 표시한다(FR-013).

## ai_credential (AI 공급자 키)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| provider | VARCHAR(20) | NOT NULL, UNIQUE | `ANTHROPIC` \| `OPENAI` (enum 확장) |
| ciphertext | BYTEA | NOT NULL | AES-256-GCM 암호문 + 인증 태그 |
| iv | BYTEA | NOT NULL, 12바이트 | |
| key_version | SMALLINT | NOT NULL | 마스터 키 버전 |
| last4 | CHAR(4) | NOT NULL | 화면 표시용 끝 4자리 |
| verified_at | TIMESTAMPTZ | NOT NULL | 마지막 검증 성공 시각 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | |

- **등록 규칙**: 공급자에 검증 요청이 성공했을 때만 저장한다(FR-023a). 이미 있는 공급자에
  다시 등록하면 기존 키를 교체한다.
- **삭제**: 물리 삭제한다. `owner.selected_ai_provider`가 이 공급자였다면 다른 키가 하나 남아
  있을 때 그 공급자로 바꾸고, 없으면 NULL로 둔다(FR-023c).

## guide_request (가이드 요청 및 기록)

하나의 테이블이 하루 한도 슬롯 예약(FR-018)과 기록 보관(FR-025)을 함께 맡는다.

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| security_id | BIGINT | FK → security, NOT NULL | 한도 단위 |
| holding_id | BIGINT | FK → holding, NULL, `ON DELETE SET NULL` | 보유 종목 삭제 후에도 기록 유지 (FR-027) |
| account_id | BIGINT | FK → account, NOT NULL | 계좌는 소프트 삭제이므로 유지. `deleted_at`으로 "삭제된 계좌" 판별 (FR-027) |
| usage_date | DATE | NOT NULL | 요청 시작 시각의 KST 날짜 |
| slot | SMALLINT | NOT NULL, 1 \| 2 | 하루 슬롯 |
| status | VARCHAR(10) | NOT NULL | `PENDING` \| `SUCCEEDED` \| `FAILED` |
| failure_kind | VARCHAR(20) | NULL | `CREDENTIAL_ERROR` \| `TRANSIENT_ERROR` \| `INVALID_STATE` |
| provider | VARCHAR(20) | NOT NULL | 요청 시 사용한 공급자 |
| model | VARCHAR(80) | NULL | 응답한 모델명 (FR-022) |
| account_name_snapshot | VARCHAR(30) | NOT NULL | 요청 당시 계좌명 |
| price_snapshot | NUMERIC(19,4) | NOT NULL | 요청 당시 현재가 |
| avg_price_snapshot | NUMERIC(19,4) | NOT NULL | 요청 당시 평단가 |
| quantity_snapshot | NUMERIC(20,6) | NOT NULL | 요청 당시 수량 |
| return_rate_snapshot | NUMERIC(9,4) | NOT NULL | 요청 당시 수익률 |
| currency | CHAR(3) | NOT NULL | |
| content | JSONB | NULL | 구조화 결과: summary, diagnosis, supplyDemand, issues[], overall, action, rationale, risks[] (supplyDemand·issues·overall은 2026-11-05 추가, 이전 기록에는 없음) |
| raw_text | TEXT | NULL | 구조화 실패 시 원문 |
| requested_at | TIMESTAMPTZ | NOT NULL | |
| completed_at | TIMESTAMPTZ | NULL | |

- **슬롯 유니크(핵심)**: `CREATE UNIQUE INDEX ux_guide_slot ON guide_request (security_id,
  usage_date, slot) WHERE status <> 'FAILED';` (FR-018, FR-021)
- **조회 인덱스**: `(security_id, requested_at DESC) WHERE status = 'SUCCEEDED'` (FR-026)
- **상태 전이**:

  ```text
  (none) ──reserve──▶ PENDING ──AI 성공──▶ SUCCEEDED   (불변, FR-028)
                         │
                         ├──AI 실패/타임아웃──▶ FAILED   (슬롯 반환, FR-020)
                         └──3분 초과 정체(정리 스케줄러)──▶ FAILED
  ```

  `SUCCEEDED`와 `FAILED`는 종료 상태다. 종료 상태 행은 수정하지 않는다. 애플리케이션에 업데이트
  경로를 두지 않고, DB 트리거로도 막는다. 단, 보유 종목 삭제 시 FK `ON DELETE SET NULL`이 `holding_id`만
  비우는 것은 트리거가 허용한다(FR-027 기록 유지).
- **남은 횟수**: `2 − count(status <> 'FAILED' AND security_id = ? AND usage_date = today_kst)`
- **스냅샷 원칙**: 보유 종목이나 계좌가 나중에 바뀌어도 기록은 요청 당시 값을 보여준다(US5 시나리오 2).

## ~~market_holiday~~ (제거)

토스증권 `GET /api/v1/market-calendar/{KR|US}`가 오늘·직전·다음 영업일을 주므로 휴장일 테이블을 두지 않는다
(research R2). 장 상태는 이 응답(하루 1회 캐시)과 정규장 시간으로 계산한다.
- KR 정규장: 09:00~15:30 KST
- US 정규장: 09:30~16:00 America/New_York. 일광 절약 시간은 `ZoneId`가 처리한다.

## market_token

| 필드 | 타입 | 설명 |
|---|---|---|
| provider | VARCHAR(10) PK | `TOSS` |
| ciphertext / iv / key_version | BYTEA / BYTEA / SMALLINT | 24시간 유효 접근 토큰 (`SecretCipher`, AES-256-GCM) |
| expires_at | TIMESTAMPTZ | |

---

## 요구사항 추적

| 요구사항 | 엔티티/규칙 |
|---|---|
| FR-001, 001a | owner (잠금 필드, 단일 행) |
| FR-002~008 | account, holding (유니크, 검증, 소프트 삭제) |
| FR-004 | security (검색 인덱스) |
| FR-009~014 | quote + 토스증권 장 운영일 API (파생 방향, 신선도) |
| FR-018~021 | guide_request (부분 유니크 슬롯, FAILED 반환) |
| FR-022, 025~028 | guide_request (스냅샷, 불변) |
| FR-023~023e | ai_credential, owner.selected_ai_provider |
