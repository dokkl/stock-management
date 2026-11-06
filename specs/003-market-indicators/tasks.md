---

description: "Task list for 003-market-indicators"
---

# Tasks: 대시보드 시장 지표 요약

**Input**: Design documents from `/specs/003-market-indicators/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 헌법 원칙 II(Test-First)에 따라 테스트 작업을 포함한다. 각 스토리의 테스트를 먼저 작성해 실패를 확인한 뒤 구현한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일, 의존 없음 → 병렬 가능
- **[Story]**: US1(국내 지수·환율), US2(미국 지표·VIX), US3(하단 문구)

## Path Conventions

`MAIN` = `src/main/java/com/stockmanagement`, `RES` = `src/main/resources`, `UT` = `src/test/java/com/stockmanagement`,
`IT` = `src/integrationTest/java/com/stockmanagement`, `E2E` = `src/e2eTest/java/com/stockmanagement`

---

## Phase 1: Setup

- [X] T001 [P] `RES/application.yml`에 `app.fred`(base-url `https://api.stlouisfed.org`, api-key `${FRED_API_KEY:}`, timeout 10s),
  `app.indicator`(stale-after 10m, domestic-interval 1m, closed-interval 30m, global-interval 1h) 설정 추가. `.env.example`에 `FRED_API_KEY=` 추가
- [X] T002 [P] `infra/terraform/parameters.tf` operator 파라미터에 `FRED_API_KEY` 추가, `infra/README.md` 4단계에 값 넣기 안내 추가

---

## Phase 2: Foundational (모든 스토리 선행)

- [X] T003 [P] 도메인 테스트 `UT/market/domain/IndicatorQuoteTest.java`: 등락 폭·등락률(소수 4자리 HALF_UP)·방향(UP/DOWN/FLAT),
  previousClose null이면 등락 없음, value ≤ 0 거부, `IndicatorStatus` 판정(국내 장중 10분 이내 LIVE / 초과 STALE / 장 외 CLOSED,
  미국 DAILY_CLOSE, 없음 UNAVAILABLE) — 분기 85% 이상
- [X] T004 도메인 구현: `MAIN/market/domain/MarketIndicator.java`(data-model 8개, 순서·단위·REALTIME/DAILY_CLOSE),
  `IndicatorQuote.java`(record: indicator, value, previousClose, asOf, asOfDate, fetchedAt + change/changeRate/direction/status),
  `IndicatorStatus.java`
- [X] T005 `RES/db/migration/V12__market_indicator_quote.sql`: data-model 테이블(PK indicator `CHECK IN (8개)`, value `NUMERIC(20,4)` > 0,
  previous_close NULL, as_of·fetched_at `TIMESTAMPTZ`, as_of_date `DATE`)
- [X] T006 포트 `MAIN/market/application/port/IndicatorStore.java`(upsert, findAll), `DomesticIndicatorSource.java`,
  `GlobalIndicatorSource.java`(인터페이스만), 저장 IT `IT/market/IndicatorStoreIT.java` 작성 후 `MAIN/market/persistence/JdbcIndicatorStore.java` 구현
- [X] T007 조회 서비스 `MAIN/market/application/MarketIndicatorQuery.java`: 8개 지표를 순서대로(없는 지표는 빈 자리), 상태 계산에
  `MarketCalendarCache`·주입 `Clock` 사용

---

## Phase 3: User Story 1 — 국내 지수와 환율 (P1) 🎯 MVP

**Goal**: 코스피·코스닥·원달러 환율 카드가 장중 1분 간격으로 갱신되어 대시보드 상단에 보인다.

**Independent Test**: WireMock 토스 대역으로 갱신 → 대시보드에 3개 카드 값·등락·"HH:mm 기준" 표시.

### Tests (먼저)

- [X] T008 [P] [US1] `IT/market/TossIndicatorSourceIT.java`: 시장 지표 현재가(timestamp null 건너뜀), 일봉 2개로 전일 종가(오늘 봉 유무),
  환율 midRate·validFrom, dateTime(직전 영업일 15:30 KST) 비교값, 4xx/5xx 시 해당 지표만 실패
- [X] T009 [P] [US1] `IT/market/MarketIndicatorRefresherIT.java`(국내): 장중 갱신 시 3개 지표 저장, 전일 종가 캐시(같은 날 캔들 재호출 없음),
  한 지표 실패해도 나머지 저장·기존 값 유지
- [X] T010 [P] [US1] `IT/market/MarketIndicatorRoutesIT.java`: `GET /` 상단 `#market-indicators`와 카드, `GET /fragments/indicators` 200·미인증 401,
  값 없음 "정보 없음", 대시보드 쿼리 수 상한(QueryCounter) 갱신

### Implementation

- [X] T011 [US1] `MAIN/market/adapter/toss/TossIndicatorSource.java`(DomesticIndicatorSource 구현, `TossHttpClient` 재사용),
  `TossRateLimiter`에 `MARKET_INDICATOR(2)`, `MARKET_INDICATOR_CHART(2)` 그룹 추가
- [X] T012 [US1] `MAIN/market/application/MarketIndicatorRefresher.java`: 국내 지표 `@Scheduled`(장중 1분, 장 외 30분), 실패 격리·경고 로그
- [X] T013 [US1] `MAIN/market/web/MarketIndicatorController.java`(`GET /fragments/indicators`)와 대시보드 모델 속성 공급,
  `RES/templates/market/indicators.html`(카드·▲/▼/–·숨김 텍스트·기준 시각 문구·4상태, htmx `every 60s`),
  `portfolio/dashboard.html` 상단 포함, `messages.properties`·`app.css`(토큰만, 모바일 2열)
- [X] T014 [US1] 숫자 표기: 지수 소수 2자리 천단위, 환율 소수 2자리 + "원" — 기존 `MoneyFormatter`/`#money`에 지표 표기 추가와 단위 테스트

**Checkpoint**: US1만으로 대시보드에 국내 지표가 보인다.

---

## Phase 4: User Story 2 — 미국 지표·WTI·VIX (P2)

**Goal**: FRED 일별 종가로 나스닥 종합·S&P 500·다우·WTI·VIX 카드가 "MM-DD 종가"와 함께 보인다.

### Tests (먼저)

- [X] T015 [P] [US2] `IT/market/FredIndicatorSourceIT.java`: 관측치 최신순 파싱, `"."` 결측 건너뛰기(최신·전일 모두), 값 없으면 결과 없음,
  API 키가 로그·예외 메시지에 남지 않음, 키 비어 있으면 호출하지 않음
- [X] T016 [P] [US2] `MarketIndicatorRefresherIT`에 미국 지표 갱신(1시간 주기 메서드) 케이스, `MarketIndicatorRoutesIT`에
  "MM-DD 종가"·FRED 출처 표기 케이스 추가

### Implementation

- [X] T017 [US2] `MAIN/market/adapter/fred/FredProperties.java`, `FredIndicatorSource.java`(GlobalIndicatorSource, 시리즈 매핑, 직접 RestClient)
- [X] T018 [US2] `MarketIndicatorRefresher`에 미국 지표 `@Scheduled`(1시간, 시작 시 1회) 추가, 템플릿에 DAILY_CLOSE 기준일 문구·출처 표기

---

## Phase 5: User Story 3 — 하단 저작권 문구 (P3)

- [X] T019 [US3] `layout.html` `footer` 프래그먼트, `dashboard.html` 포함, `app.copyright` 메시지, `.site-footer` 스타일, `AccountRoutesIT.dashboardShowsCopyrightFooter`

---

## Phase 5b: 개정 — 최신 종가 제공처 (research R3-개정)

- [X] T024 [US2] `IT/market/LatestCloseSourcesIT.java`(Cboe CSV 마지막 두 행, 야후 null 종가 건너뛰기·뉴욕 날짜, 실패 시 빈 결과)와
  `MarketIndicatorRefresherIT` 최신 날짜 선택·FRED 대체 케이스
- [X] T025 [US2] `MAIN/market/adapter/cboe/CboeIndicatorSource.java`, `adapter/yahoo/YahooIndicatorSource.java`(+Properties),
  `MarketIndicatorRefresher` 제공처 중 최신 날짜 선택, 출처 문구 갱신
- [X] T026 [US1] 장 마감 후 토스 시장 지표 `timestamp: null` 처리(값 유지, 기준 시각은 정규장 종료 또는 수신 시각)

## Phase 6: Polish & Cross-Cutting

- [X] T020 [P] `E2E/market/MarketIndicatorsE2E.java`: 데스크톱 8개 카드 순서·등락 기호, 모바일 390px 가로 넘침 없음, axe 통과
- [X] T021 [P] ArchitectureTest 통과 확인(market web→application→domain, adapter는 port 구현), JaCoCo 도메인 기준 충족
- [ ] T022 운영 반영: SSM `FRED_API_KEY` 값 입력, `terraform apply`(파라미터 추가), 배포 후 quickstart 수동 확인
- [ ] T023 `./gradlew check` 전체 통과

---

## Dependencies & Execution Order

- Phase 1 → Phase 2(T003→T004→T005/T006→T007) → US1(T008~T010 → T011~T014) → US2(T015/T016 → T017~T018) → Polish
- US3(T019)는 완료. US2는 Phase 2와 T013(템플릿)에 의존.

## Parallel Example

```text
T008, T009, T010 (US1 테스트, 서로 다른 파일)
T015, T016 (US2 테스트)
T020, T021 (Polish)
```

## Implementation Strategy

MVP = Phase 1~3(국내 지표). 이어서 US2(미국 지표)를 더하고 Polish에서 E2E·운영 반영.
