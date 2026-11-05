# Contract: Web Routes (Thymeleaf + htmx)

**Feature**: `001-stock-portfolio-ai-guide`

모든 화면은 서버 렌더링 HTML이다. `HX-Request: true` 헤더가 있으면 전체 페이지 대신 **프래그먼트**만
반환한다. 상태를 바꾸는 요청은 모두 CSRF 토큰이 필요하다(htmx는 `X-CSRF-TOKEN` 헤더로 보낸다).
아래 경로는 `/setup`, `/login`, 정적 리소스를 빼면 모두 인증이 필요하다.

공통 오류 응답 규칙:

| 상황 | 일반 요청 | htmx 요청 |
|---|---|---|
| 검증 실패 | 200 + 폼 재렌더링(필드별 오류) | 422 + 폼 프래그먼트 |
| 리소스 없음 | 404 페이지 | 404 + 토스트 프래그먼트 |
| 미인증 | 302 → `/login` | 401 + `HX-Redirect: /login` |
| 서버 오류 | 500 페이지(내부 정보 없음) | 500 + 토스트 프래그먼트 |

---

## 인증 (FR-001, FR-001a)

| Method | Path | 설명 | 성공 | 실패 |
|---|---|---|---|---|
| GET | `/setup` | 최초 비밀번호 설정 화면. owner 행이 있으면 404 | 200 | 404 |
| POST | `/setup` | `password`, `passwordConfirm` (12자 이상, 일치) | 302 → `/login` | 200 폼 오류 |
| GET | `/login` | 로그인 화면 | 200 | — |
| POST | `/login` | `password` | 302 → `/` | 302 → `/login?error` / `?locked` (남은 잠금 시간 표시) |
| POST | `/logout` | | 302 → `/login` | — |

- owner가 없으면 `/setup` 외 모든 경로는 302 → `/setup`으로 보낸다.

## 포트폴리오 (US1, US2)

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| GET | `/` | 계좌별 보유 목록 대시보드 | 200 전체 페이지 |
| GET | `/fragments/quotes` | 모든 보유 종목의 시세 셀 (htmx `every 30s` 폴링) | 200 프래그먼트, `hx-swap-oob` 셀 목록 |
| POST | `/accounts` | `name` | 201 → 계좌 섹션 프래그먼트 / 422(검증·중복·계좌 2개 상한 FR-002a) |
| PUT | `/accounts/{id}` | `name` | 200 계좌 헤더 프래그먼트 / 422 / 404 |
| DELETE | `/accounts/{id}` | 확인 대화상자 후 호출 | 200 빈 프래그먼트 + 토스트 / 404 |
| GET | `/accounts/{id}/edit` | 계좌 이름 변경 폼 프래그먼트 | 200 / 404 |
| GET | `/accounts/{id}/header` | 계좌 헤더 프래그먼트(이름 변경 취소) | 200 / 404 |
| GET | `/securities/search?q=&market=` | 종목 검색 자동완성. `q`는 1~40자, 최대 20건 | 200 목록 프래그먼트 |
| POST | `/accounts/{id}/holdings` | `securityId`, `avgPrice`, `quantity` | 201 행 프래그먼트 / 422(검증·중복 FR-006·보유 20개 상한 FR-003a) |
| PUT | `/holdings/{id}` | `avgPrice`, `quantity` | 200 행 프래그먼트 / 422 / 404 |
| DELETE | `/holdings/{id}` | | 200 + 토스트 / 404 |
| GET | `/holdings/{id}` | 보유 종목 행 프래그먼트(수정 취소) | 200 / 404 |
| GET | `/holdings/{id}/edit` | 보유 종목 인라인 수정 행 프래그먼트 | 200 / 404 |

### 대시보드 행 렌더링 계약 (FR-009~FR-014)

각 보유 종목 행은 다음 데이터를 표시한다.

| 항목 | 형식 예 | 규칙 |
|---|---|---|
| 종목명 / 코드 | `삼성전자 005930` · `Apple AAPL` | |
| 현재가 | `₩71,000` · `$187.50` | 통화별 포매터 |
| 전일 대비 | `▲ 1,000 (+1.43%)` · `▼ 2.50 (−1.32%)` · `– 0 (0.00%)` | `class="up|down|flat"`, 기호 필수 |
| 평단가 · 수량 | `₩71,500 · 10주` · `$185.2000 · 0.35주` | |
| 평가손익 · 수익률 | `▼ ₩5,000 (−0.70%)` | 같은 색 규칙 |
| 시세 상태 | `14:32:05 기준` / `지연 (14:20 기준)` / `시세 없음` / `장 시작 전` / `장 마감` / `거래정지` | FR-012~014 |
| 가이드 버튼 | `가이드 (남은 2회)` | 비활성 조건은 아래 참조 |

## AI 가이드 (US3, US5)

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| POST | `/holdings/{id}/guides` | 가이드 요청(슬롯 예약 + 비동기 실행) | 아래 표 |
| GET | `/guides/{id}/status` | 진행 상태 폴링(htmx `every 2s`, 진행 카드 안의 숨은 폴러) | `PENDING`: 204 본문 없음(진행 카드와 애니메이션 유지, 폴링 계속) / `SUCCEEDED`: 200 결과 프래그먼트 + `HX-Trigger: guide-quota-changed` / `FAILED`: 200 오류 프래그먼트(폴링 중단) |
| GET | `/guides/{id}` | 가이드 상세(결과 전체) | 200 / 404 |
| GET | `/holdings/{id}/guide-button` | 가이드 버튼(남은 횟수·비활성 사유) 프래그먼트. `guide-quota-changed-{securityId}` 이벤트로 다시 받는다 | 200 / 404 |
| GET | `/securities/{id}/guides?page=` | 종목별 가이드 기록, 최신순, 20건 페이지 | 200 |

### `POST /holdings/{id}/guides` 응답

| 조건 | 상태 | 본문 |
|---|---|---|
| 슬롯 예약 성공 | 202 | 분석 진행 카드(단계 목록·진행 막대·경과 시간, 숨은 폴러 `hx-get=/guides/{gid}/status` `hx-trigger="every 2s"`) |
| 등록된 AI 키 없음 (FR-023d) | 409 | `HX-Redirect: /settings/ai` |
| 오늘 이 종목 2회 사용 (FR-018) | 429 | 한도 안내 프래그먼트 + 초기화 시각 `내일 00:00 (KST)` |
| 거래정지·상장폐지 종목 | 409 | 비활성 안내 |
| holding 없음 | 404 | |

- 202와 `/status`의 SUCCEEDED·FAILED 응답은 `HX-Trigger: guide-quota-changed, guide-quota-changed-{securityId}`를 보낸다.
  버튼은 종목별 이벤트만 듣는다(같은 종목을 여러 계좌에 보유하면 그 행들이 함께 갱신된다).
- 시세가 아직 없는 종목은 409로 안내한다.
- 같은 종목에서 `PENDING`이 이미 있으면 새 슬롯을 잡지 않고, 기존 요청의 진행 프래그먼트를
  반환한다(202). 연속 클릭을 이렇게 방지한다(US3 시나리오 7).
- 실패 프래그먼트 문구는 `failure_kind`에 따라 다르다.
  - `CREDENTIAL_ERROR`: "AI 키를 확인해 주세요" + 설정 링크
  - `TRANSIENT_ERROR`: "AI 서비스가 일시적으로 응답하지 않습니다. 사용 횟수는 차감되지 않았습니다."

## AI 설정 (US4)

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| GET | `/settings/ai` | 공급자 목록, 각 키 상태(`****1234` / `키 필요`), 선택된 공급자 | 200 |
| PUT | `/settings/ai/credentials/{provider}` | `apiKey`(원문). 검증 후 저장·교체 | 200 행 프래그먼트 / 422 "키를 확인할 수 없습니다" / 503 검증 일시 장애 |
| DELETE | `/settings/ai/credentials/{provider}` | 확인 후 삭제 | 200 |
| PUT | `/settings/ai/selection` | `provider` (키가 있는 공급자만) | 200 / 422 |

- `apiKey`는 응답, 로그, 리다이렉트 URL, 폼 재렌더링 어디에도 다시 나오지 않는다. 검증에 실패해서
  폼을 다시 그릴 때도 입력란은 비운다(SC-009).
