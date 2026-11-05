---

description: "Task list for 001-stock-portfolio-ai-guide"
---

# Tasks: 계좌별 주식 보유 관리와 AI 투자 가이드

**Input**: Design documents from `/specs/001-stock-portfolio-ai-guide/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 헌법 원칙 II(Test-First, NON-NEGOTIABLE)에 따라 **테스트 작업을 포함한다**. 각 스토리의 테스트
작업을 먼저 수행하고, 실패(Red)를 확인한 뒤 구현한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1~US5)
- Include exact file paths in descriptions

## Path Conventions

단일 Gradle 모듈(plan.md Structure Decision). 약어:

- `MAIN` = `src/main/java/com/stockmanagement`
- `RES` = `src/main/resources`
- `UT` = `src/test/java/com/stockmanagement` (단위 + ArchUnit)
- `IT` = `src/integrationTest/java/com/stockmanagement` (Testcontainers + WireMock)
- `E2E` = `src/e2eTest/java/com/stockmanagement` (Playwright + axe)

Flyway 마이그레이션은 스토리별 파일로 나누고 버전 번호를 미리 예약한다. 병렬 작업 시 파일이 충돌하지
않게 하기 위해서다: V1(owner, Foundational), V2(market_token, Foundational), V3(security, US1),
V4(portfolio, US1), V5(quote, US2), V6(ai_credential, US4), V7(guide_request, US3).
US4를 US2보다 먼저 머지하면 로컬 DB에서 V6이 V5보다 먼저 적용된다. 이 경우 개발 DB는
`docker compose down -v`로 초기화한다(운영 배포 전이므로 `out-of-order`는 켜지 않는다).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 프로젝트 골격, 빌드, 품질 도구, 컨테이너 실행 환경

- [X] T001 Gradle 9 Wrapper와 Kotlin DSL 프로젝트를 만든다: `settings.gradle.kts`(rootProject.name="stock-management"), `build.gradle.kts`(Java toolchain 25, group `com.stockmanagement`), `gradle/libs.versions.toml`(Spring Boot 4.1.2, Spring AI BOM 2.0.2, Testcontainers BOM 2.0.5, Flyway, ArchUnit 1.5.1, Playwright 1.64.0, WireMock, htmx WebJar 2.x)
- [X] T002 `build.gradle.kts`에 의존성을 추가한다:
  - 메인: spring-boot-starter-web, thymeleaf, security, data-jpa, validation, actuator, `spring-boot-docker-compose`(developmentOnly), flyway-core, flyway-database-postgresql, postgresql, spring-ai-anthropic, spring-ai-openai(자동 구성 starter가 아닌 모델 모듈), thymeleaf-extras-springsecurity6, htmx WebJar
  - 테스트: AssertJ, Mockito, ArchUnit
- [X] T003 `build.gradle.kts`에 JVM Test Suite `integrationTest`(소스셋 `src/integrationTest/java`, Testcontainers postgresql, WireMock)와 `e2eTest`(소스셋 `src/e2eTest/java`, Playwright, axe-core)를 정의하고 `check`가 두 스위트에 의존하도록 연결한다
- [X] T004 [P] Spotless(palantir-java-format)와 Error Prone을 설정하고 `options.compilerArgs += "-Werror"`로 경고를 실패로 처리한다. 파일은 `build.gradle.kts`
- [X] T005 [P] Checkstyle 규칙을 작성한다: `CyclomaticComplexity` max=10, `MethodLength` max=50, `EmptyCatchBlock`, `UnusedImports`, `maxWarnings = 0`. 파일은 `config/checkstyle/checkstyle.xml`
- [X] T006 [P] JaCoCo 커버리지 검증 규칙을 `build.gradle.kts`에 정의한다:
  - 전체 LINE ≥ 0.80
  - 패키지 `com.stockmanagement.*.domain*` LINE ≥ 0.90, BRANCH ≥ 0.85
  - `check`에 연결
- [X] T007 [P] OWASP Dependency-Check 플러그인을 `failBuildOnCVSS = 7.0`으로 설정한다(`build.gradle.kts`). gitleaks 설정은 `.gitleaks.toml`에 둔다
- [X] T008 [P] `.gitignore`(build/, .gradle/, .env, *.iml)와 `.env.example`을 만든다. `.env.example`의 키: `APP_CRYPTO_KEY`, `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET`, `TOSS_BASE_URL`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- [X] T009 [P] `Dockerfile`을 멀티 스테이지로 만든다:
  - 빌드: `eclipse-temurin:25-jdk`, `./gradlew bootJar`
  - 실행: `eclipse-temurin:25-jre`, layertools 추출, non-root 사용자 `app`, `EXPOSE 8080`
- [X] T010 [P] `compose.yaml`을 만든다:
  - `db`: `postgres:18`, named volume, `pg_isready` healthcheck
  - `app`: build `.`, `env_file: .env`, `depends_on: db (service_healthy)`, 8080 포트
  - `k6`: `grafana/k6`, `profiles: [perf]`, `./perf` 마운트
- [X] T011 [P] GitHub Actions CI를 `.github/workflows/ci.yml`에 만든다(헌법 Quality Gates):
  - 트리거: pull_request, push(main)
  - `actions/setup-java` Temurin 25, Gradle 캐시
  - `./gradlew check`(spotless, checkstyle, error prone, test, integrationTest, e2eTest, jacoco verification). Testcontainers용 Docker는 ubuntu-latest 러너 기본 제공
  - `./gradlew dependencyCheckAnalyze`(CVSS ≥ 7 실패)
  - `gitleaks/gitleaks-action`으로 비밀정보 검사
  - 실패 시 테스트·커버리지 리포트를 아티팩트로 업로드
- [X] T012 `StockManagementApplication` 진입점을 `MAIN/StockManagementApplication.java`에 만든다. `RES/application.yml`에 다음을 둔다:
  - datasource 환경 변수
  - `spring.jpa.open-in-view=false`
  - `spring.threads.virtual.enabled=true`
  - `server.servlet.session.timeout=7d`
  - `app.zone=Asia/Seoul`
  - `app.ai.models.anthropic`, `app.ai.models.openai`, `app.ai.timeout=60s`
  - `app.portfolio.max-holdings=500`
  - `app.toss.*`, `app.quote.refresh-interval=30s`, `app.quote.stale-after=1m`(SC-003)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 의존하는 공통 도메인, 인증(FR-001), UI 골격, 아키텍처 규칙, 테스트 인프라, 토스증권 연동 기반

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests (먼저 작성, 실패 확인)

- [X] T013 [P] ArchUnit 아키텍처 테스트를 `UT/ArchitectureTest.java`에 작성한다:
  - `..domain..`은 `..application..`, `..web..`, `..persistence..`, `..adapter..`, `org.springframework..`에 의존하지 않는다
  - `..web..`은 `..persistence..`와 `..adapter..`에 의존하지 않는다
  - `..domain..` 클래스는 `double`/`float` 필드를 갖지 않는다(헌법 원칙 I)
  - 모든 `..application.port..` 타입은 interface다
- [X] T014 [P] `Money` 단위 테스트를 `UT/common/domain/MoneyTest.java`에 작성한다:
  - KRW scale 0 강제(소수 입력 시 예외)
  - USD scale ≤ 4
  - 다른 통화끼리 연산하면 예외
  - 덧셈·뺄셈·곱셈(수량)·비율 계산은 HALF_UP 소수 넷째 자리
- [X] T015 [P] `Direction.of(BigDecimal)` 단위 테스트(양수 UP, 음수 DOWN, 0 FLAT)를 `UT/common/domain/DirectionTest.java`에 작성한다
- [X] T016 [P] `KstDate.today(Clock)` 단위 테스트를 `UT/common/domain/KstDateTest.java`에 작성한다. 2026-11-05T14:59:59Z는 2026-11-05 KST, 2026-11-05T15:00:00Z는 2026-11-06 KST다
- [X] T017 [P] `MoneyFormatter` 단위 테스트를 `UT/common/web/MoneyFormatterTest.java`에 작성한다:
  - `₩71,000`, `$187.50`, `$185.2000`(평단가 4자리)
  - 등락 `▲ 1,000 (+1.43%)`, `▼ 2.50 (−1.32%)`, `– 0 (0.00%)`
  - 수량 `10주`, `0.35주`
- [X] T018 [P] `LoginLockPolicy` 단위 테스트를 `UT/auth/domain/LoginLockPolicyTest.java`에 작성한다(`MutableClock` 사용):
  - 4회 실패 시 잠금 없음
  - 5회째 실패 시 `locked_until = now + 15m`, 카운터 0으로 초기화
  - 잠금 중이면 올바른 비밀번호도 거부
  - 15분 경과 후 해제
  - 성공 시 카운터 0
- [X] T019 [P] 인증 통합 테스트를 `IT/auth/AuthFlowIT.java`에 작성한다:
  - owner 없음: `/`가 302 → `/setup`
  - `POST /setup`에 12자 미만이거나 불일치하면 폼 오류
  - 정상 설정 후 `/setup`은 404
  - 로그인 성공 302 → `/`
  - 5회 실패 후 `/login?locked`
  - 미인증 htmx 요청은 401 + `HX-Redirect: /login`
  - CSRF 토큰 없는 POST는 403
- [X] T020 [P] 테스트 지원 클래스를 만든다:
  - `UT/support/MutableClock.java`
  - `IT/support/IntegrationTestBase.java`: `@SpringBootTest`, PostgreSQL 18 Testcontainer 싱글턴, `@ServiceConnection`, WireMock 서버를 `app.toss.base-url`·AI base-url에 연결, 각 테스트 후 테이블 truncate
  - `IT/support/LoggedInOwner.java`: owner 생성과 MockMvc 로그인 세션 헬퍼
- [X] T021 [P] `AesGcmSecretCipher` 단위 테스트를 `UT/common/crypto/AesGcmSecretCipherTest.java`에 작성한다:
  - 왕복 복호화
  - 같은 평문이면 다른 암호문(IV 무작위, 12바이트)
  - 암호문·IV 1바이트 변조 시 예외이고, 예외 메시지에 평문 없음
  - `APP_CRYPTO_KEY`가 32바이트(Base64 디코딩 후)가 아니면 생성 실패
- [X] T022 [P] 로그인·설정 화면 E2E를 `E2E/auth/AuthE2E.java`에 작성한다: quickstart S1 1~3단계, 두 화면 모두 axe 위반 0건
- [X] T023 [P] 세션 비활성 만료 7일 검증을 `IT/auth/SessionTimeoutIT.java`에 작성한다(FR-001a): `server.servlet.session.timeout`이 `7d`로 바인딩되고 세션 쿠키가 `HttpOnly`인지 확인

### Implementation

- [X] T024 Flyway `RES/db/migration/V1__owner.sql`을 작성한다:
  - `CREATE EXTENSION IF NOT EXISTS pg_trgm`
  - `owner` 테이블: id BIGINT PK `CHECK (id = 1)`, password_hash VARCHAR(255) NOT NULL, failed_attempts INT NOT NULL DEFAULT 0 `CHECK (failed_attempts >= 0)`, locked_until TIMESTAMPTZ NULL, selected_ai_provider VARCHAR(20) NULL, created_at/updated_at TIMESTAMPTZ NOT NULL
- [X] T025 [P] 공통 도메인을 `MAIN/common/domain/`에 구현한다:
  - `Currency` enum: KRW(scale 0), USD(평단가 scale 4, 표시 scale 2)
  - `Money` record: BigDecimal amount, Currency
  - `Direction` enum: UP / DOWN / FLAT
  - `KstDate` 유틸: `ZoneId.of("Asia/Seoul")`
  - T014~T016를 통과시킨다
- [X] T026 [P] `ClockConfig`(`Clock.system(ZoneId.of("Asia/Seoul"))` 빈)와 `AsyncConfig`(가상 스레드 `TaskExecutor` 빈 이름 `guideExecutor`)를 `MAIN/common/config/`에 구현한다
- [X] T027 `Owner` 도메인과 `LoginLockPolicy`를 `MAIN/auth/domain/`에 구현한다(T018 통과). JPA 엔티티 `OwnerEntity`와 `OwnerRepository`는 `MAIN/auth/persistence/`에 만든다
- [X] T028 `SetupService`와 `LoginAttemptService`를 `MAIN/auth/application/`에 구현한다:
  - 비밀번호 12자 이상, Argon2id `PasswordEncoder`
  - owner 행이 이미 있으면 `SetupAlreadyCompletedException`
  - 잠금 판정은 `Clock` 사용
- [X] T029 `SecurityConfig`를 `MAIN/common/config/SecurityConfig.java`에 구현한다:
  - 폼 로그인 `/login`(username 필드 없이 password만 받는 커스텀 `AuthenticationProvider`)
  - 허용 경로: `/setup`, `/login`, `/css/**`, `/webjars/**`, `/actuator/health`
  - CSRF 활성, htmx용 `X-CSRF-TOKEN` 헤더
  - 미인증 htmx 요청은 401 + `HX-Redirect`
  - owner 미설정 시 `/setup` 리다이렉트 필터
  - 로그인 실패·성공 핸들러가 `LoginAttemptService`를 호출
- [X] T030 `SetupController`와 `LoginController`를 `MAIN/auth/web/`에 구현한다. 템플릿은 `RES/templates/auth/setup.html`, `RES/templates/auth/login.html`이고, 잠금이면 남은 분을 표시한다. T019을 통과시킨다
- [X] T031 [P] 디자인 토큰을 `RES/static/css/tokens.css`에 정의한다:
  - `--color-up: #d32f2f`, `--color-down: #1565c0`, `--color-flat: #616161`
  - 간격, 타이포, 반경, 포커스 링 변수
  - 텍스트 대비 4.5:1 이상
  - 기본 스타일은 `RES/static/css/app.css`에 둔다. 인라인 style은 금지한다
- [X] T032 [P] 공용 Thymeleaf 프래그먼트를 `RES/templates/fragments/`에 만든다:
  - `button.html`: primary / secondary / danger, `aria-busy`
  - `field.html`: label, 입력, 필드 오류 `aria-describedby`
  - `confirm-dialog.html`: `<dialog>` 기반 확인 대화상자
  - `toast.html`: 우측 상단, 4초
  - `states.html`: loading / empty / error / ready
- [X] T033 [P] `RES/templates/layout.html`을 만든다: 헤더, 네비게이션(대시보드, AI 설정, 로그아웃), htmx WebJar 스크립트, `hx-headers` CSRF, 토스트 영역, 공통 `hx-indicator`
- [X] T034 [P] 메시지 카탈로그 `RES/messages.properties`(한국어)를 만든다. 공통 오류, 인증, 확인 대화상자, 면책 문구를 넣는다. 면책 문구 키는 `guide.disclaimer`, 문구는 "본 가이드는 투자 참고 정보이며 투자 자문이 아닙니다. 투자 판단의 책임은 사용자에게 있습니다."다
- [X] T035 `MoneyFormatter`와 Thymeleaf 표현식 유틸리티 `#money`(Dialect)를 `MAIN/common/web/`에 구현한다(T017 통과)
- [X] T036 `GlobalExceptionHandler`와 `HtmxSupport`를 `MAIN/common/web/`에 구현한다:
  - `HtmxSupport`: `HX-Request` 판별, `HX-Redirect`·`HX-Trigger` 헤더 헬퍼
  - 검증 실패 처리: htmx 요청이면 422 + 폼 프래그먼트
  - 404 처리: htmx 요청이면 404 + 토스트
  - 500 처리: 내부 정보 없는 페이지와 토스트
  - contracts/web-routes.md 공통 오류 표를 따르고, 예외는 맥락과 함께 로깅한다
- [X] T037 [P] 토스증권 토큰 통합 테스트를 `IT/market/TossTokenStoreIT.java`에 작성한다(WireMock, `POST /oauth2/token` form): 최초 발급, 유효 시 재사용, 만료 5분 전 재발급, 무효화 후 재발급, DB 저장 값이 평문 토큰과 다름
- [X] T038 `SecretCipher` 인터페이스와 `EncryptedSecret`(ciphertext, iv, keyVersion) record를 `MAIN/common/crypto/`에, `AesGcmSecretCipher`를 `MAIN/common/crypto/AesGcmSecretCipher.java`에 구현한다. AES/GCM/NoPadding, 128비트 태그, `SecureRandom` 12바이트 IV, `key_version` 1, 키는 `app.crypto.key`(환경 변수 `APP_CRYPTO_KEY`, Base64 32바이트)이다(T021 통과)
- [X] T039 토스증권 연동 기반을 `MAIN/market/adapter/toss/`에 구현한다(research R2, 2026-11-05 KIS에서 교체):
  - `TossProperties`(`app.toss.base-url`, `client-id`, `client-secret`, `timeout`)
  - `TossHttpClient`: Spring `RestClient`, HTTP/1.1, 타임아웃 10s, `{"result": ...}` 봉투 해제
  - `TossRateLimiter`: Rate Limits Group별 간격 제어(AUTH 4, STOCK_ALL 1, MARKET_INFO 2, MARKET_DATA 12, MARKET_DATA_CHART 15회/초)
  - `TossTokenStore`: `market_token`에 접근 토큰을 `SecretCipher`(T038)로 암호화 저장, 만료 5분 전 재발급, 401 시 `invalidate()`
  - `market_token` 테이블은 `RES/db/migration/V2__market_token.sql`로 만든다: provider VARCHAR(10) PK, ciphertext BYTEA NOT NULL, iv BYTEA NOT NULL, key_version SMALLINT NOT NULL, expires_at TIMESTAMPTZ NOT NULL

**Checkpoint**: 로그인 가능한 빈 레이아웃이 뜨고 `./gradlew check`(단위 + 통합)가 통과한다. 이제 스토리 작업을 시작할 수 있다.

---

## Phase 3: User Story 1 - 계좌별 보유 종목 등록 (Priority: P1) 🎯 MVP

**Goal**: 계좌를 만들고 한국·미국 종목을 검색해 평단가·수량과 함께 등록·수정·삭제한다.

**Independent Test**: 계좌 2개를 만들고 한국·미국 종목을 1개씩 등록한다. 계좌별로 종목·평단가(통화)·수량이 맞게 보이는지, 중복과 0 이하 값이 거부되는지, 수정·삭제가 반영되는지 확인한다(quickstart S2).

### Tests for User Story 1 ⚠️ (먼저 작성, 실패 확인)

- [X] T040 [P] [US1] `Account` 도메인 단위 테스트를 `UT/portfolio/domain/AccountTest.java`에 작성한다: 이름은 "1~30자(앞뒤 공백 제거 후)", 빈 문자열과 31자는 예외
- [X] T041 [P] [US1] `Holding` 도메인 단위 테스트를 `UT/portfolio/domain/HoldingTest.java`에 작성한다:
  - avg_price "> 0", quantity "> 0"
  - KRW 평단가 소수 자릿수 0
  - USD 평단가 소수 자릿수 ≤ 4
  - KR 종목 수량은 정수
  - US 종목 수량은 소수 6자리까지
  - 보유 종목 상한: 소유자 전체 보유 종목 수가 `app.portfolio.max-holdings`(500)에 도달하면 새 등록은 `HoldingLimitExceededException`(헌법 원칙 IV 예외, plan.md Complexity Tracking)
- [X] T042 [P] [US1] 종목 검색 통합 테스트를 `IT/market/SecuritySearchIT.java`에 작성한다:
  - 픽스처: 삼성전자 005930 KOSPI, Apple AAPL NASDAQ
  - `q`는 1~40자, 최대 20건
  - 한글명 부분 일치, 티커 prefix, `market=US` 필터
  - `DELISTED`는 결과에서 제외
  - `q` 빈 문자열·41자 이상은 422
  - 미인증 401
- [X] T043 [P] [US1] 계좌 라우트 계약 테스트를 `IT/portfolio/AccountRoutesIT.java`에 작성한다:
  - `POST /accounts`: 201 / 422(빈 이름, 31자, 중복 이름)
  - `PUT /accounts/{id}`: 200 / 422 / 404
  - `DELETE /accounts/{id}`: 200이면 `deleted_at` 설정 + 하위 holding 삭제 / 404
  - 미인증 401
- [X] T044 [P] [US1] 보유 종목 라우트 계약 테스트를 `IT/portfolio/HoldingRoutesIT.java`에 작성한다:
  - `POST /accounts/{id}/holdings`: 201 / 422(평단가 0, 수량 0, KRW 소수, 같은 계좌 같은 종목 중복 FR-006)
  - `PUT /holdings/{id}`: 200 / 422 / 404
  - `DELETE /holdings/{id}`: 200 / 404
  - 삭제된 계좌에 등록하면 404
  - 중복 등록 422 응답에 "이미 등록된 종목입니다. 기존 항목을 수정하세요" 문구와 기존 행 편집 링크 포함(US1 시나리오 4)
  - 보유 종목 500건 상태에서 등록하면 422 "보유 종목은 최대 500개까지 등록할 수 있습니다"
  - 미인증 401, CSRF 없음 403
- [X] T045 [P] [US1] 대시보드 조회 통합 테스트를 `IT/portfolio/DashboardQueryIT.java`에 작성한다:
  - 계좌별 그룹, 통화별 평단가 표시
  - 삭제된 계좌 제외
  - 보유 500건 시드에서 Hibernate 통계·쿼리 카운터로 SQL 실행이 2회 이하인지 확인(N+1 금지)
- [X] T046 [P] [US1] E2E 시나리오를 `E2E/portfolio/RegisterHoldingsE2E.java`에 작성한다:
  - quickstart S2의 1~5단계: 계좌 생성, 삼성전자 `₩71,500 · 10주`, AAPL `$185.2000 · 0.35주`, 중복 오류, 평단가 0 오류, 계좌 삭제 확인 대화상자, 보유 종목 삭제 확인 대화상자(FR-008)
  - axe 위반 0건

- [X] T047 [P] [US1] 종목 마스터 동기화 통합 테스트를 `IT/market/SecurityMasterSyncIT.java`에 작성한다(WireMock `GET /api/v1/stocks/all`): 신규 삽입, 이름 변경 반영, 누락 종목 `DELISTED`, `STOCK_WARRANTS` 제외, 재실행 시 멱등, 401 시 토큰 재발급 후 재시도

### Implementation for User Story 1

- [X] T048 [US1] Flyway `RES/db/migration/V3__security.sql`을 작성한다:
  - `security` 테이블: id BIGINT PK, market VARCHAR(2) NOT NULL `CHECK (market IN ('KR','US'))`, exchange VARCHAR(10) NOT NULL `CHECK (exchange IN ('KOSPI','KOSDAQ','NYSE','NASDAQ','AMEX'))`, symbol VARCHAR(12) NOT NULL, name_ko VARCHAR(100) NULL, name_en VARCHAR(150) NULL, currency CHAR(3) NOT NULL, trading_status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' `CHECK (trading_status IN ('ACTIVE','SUSPENDED','DELISTED'))`, updated_at TIMESTAMPTZ NOT NULL
  - `UNIQUE (exchange, symbol)`
  - `CHECK ((market='KR') = (currency='KRW'))`
  - name_ko·name_en에 `gin_trgm_ops` GIN 인덱스, symbol에 `text_pattern_ops` 인덱스
- [X] T049 [US1] Flyway `RES/db/migration/V4__portfolio.sql`을 작성한다:
  - `account` 테이블: id BIGINT PK, name VARCHAR(30) NOT NULL, deleted_at TIMESTAMPTZ NULL, created_at/updated_at
  - `CREATE UNIQUE INDEX ux_account_name ON account(name) WHERE deleted_at IS NULL`
  - `holding` 테이블: id BIGINT PK, account_id FK NOT NULL, security_id FK NOT NULL, avg_price NUMERIC(19,4) NOT NULL `CHECK (avg_price > 0)`, quantity NUMERIC(20,6) NOT NULL `CHECK (quantity > 0)`, created_at/updated_at, `UNIQUE (account_id, security_id)`
- [X] T050 [P] [US1] `Security`, `Market`, `Exchange`, `TradingStatus`를 `MAIN/market/domain/`에 구현한다. 불변식은 "market = KR ⇔ currency = KRW"다
- [X] T051 [P] [US1] `Account`와 `Holding` 도메인을 `MAIN/portfolio/domain/`에 구현한다. 생성자에서 T040·T041 규칙을 검증하고, 위반 시 필드 키를 가진 `DomainValidationException`을 던진다
- [X] T052 [US1] JPA 엔티티와 리포지토리를 만든다:
  - `SecurityEntity`·`SecurityRepository`: `MAIN/market/persistence/`
  - `AccountEntity`·`HoldingEntity`·`AccountRepository`·`HoldingRepository`: `MAIN/portfolio/persistence/`
  - 도메인 ↔ 엔티티 매퍼를 둔다
- [X] T053 [US1] `SecuritySearch` 서비스를 `MAIN/market/application/SecuritySearch.java`에 구현한다:
  - 구현은 `MAIN/market/persistence/SecuritySearchJdbcQuery.java`의 `JdbcClient` 쿼리 1회
  - `symbol ILIKE q%` OR `similarity(name_ko|name_en, q) > 0.2`, `trading_status <> 'DELISTED'`, LIMIT 20
  - T042을 통과시킨다
- [X] T054 [US1] `AccountService`(생성·이름 변경·소프트 삭제 + 하위 holding 삭제, 한 트랜잭션)와 `HoldingService`(등록·수정·삭제, 중복 시 `DuplicateHoldingException`, 등록 전 전체 보유 수가 `app.portfolio.max-holdings` 이상이면 `HoldingLimitExceededException`)를 `MAIN/portfolio/application/`에 구현한다
- [X] T055 [US1] `DashboardQuery` 포트를 `MAIN/portfolio/application/DashboardQuery.java`에, 구현을 `MAIN/portfolio/persistence/DashboardJdbcQuery.java`에 만든다:
  - account LEFT JOIN holding JOIN security, 단일 쿼리, `deleted_at IS NULL`, 계좌명·종목명 정렬
  - 반환 DTO는 `DashboardView`(계좌 목록 → 보유 행 목록)
  - 이 단계에서는 시세 열을 비워 두고, US2에서 LEFT JOIN quote로 확장한다
- [X] T056 [US1] `SecuritySearchController`(`GET /securities/search?q=&market=`)를 `MAIN/market/web/`에, 자동완성 프래그먼트를 `RES/templates/portfolio/security-search-results.html`에 구현한다. 키보드로 선택할 수 있게 `role="listbox"`를 쓴다
- [X] T057 [US1] `AccountController`(`POST /accounts`, `PUT /accounts/{id}`, `DELETE /accounts/{id}`)와 `HoldingController`(`POST /accounts/{id}/holdings`, `PUT /holdings/{id}`, `DELETE /holdings/{id}`)를 `MAIN/portfolio/web/`에 구현한다. 상태 코드와 프래그먼트는 contracts/web-routes.md를 따른다. T043·T044를 통과시킨다
- [X] T058 [US1] `DashboardController`(`GET /`)와 템플릿을 구현한다:
  - `RES/templates/portfolio/dashboard.html`: 계좌 섹션 접기/펼치기, 빈 상태 "계좌를 추가해 보세요"
  - `account-section.html`, `holding-row.html`, `holding-form.html`: 종목 검색, 평단가, 수량
  - 삭제는 공용 confirm-dialog를 쓴다
  - 문구는 `messages.properties`의 `portfolio.*` 키를 쓴다
  - T045·T046을 통과시킨다
- [X] T059 [US1] `SecurityMasterSync`를 `MAIN/market/application/SecurityMasterSync.java`에 구현한다:
  - `MarketDataProvider.fetchSecurityMaster(Exchange)` 포트(`MAIN/market/application/port/MarketDataProvider.java`, contracts/ports.md) 사용
  - 매일 06:00 KST와 기동 시 security 테이블이 비어 있으면 실행
  - upsert, 사라진 종목은 `DELISTED`
- [X] T060 [US1] 토스증권 종목 마스터 어댑터를 `MAIN/market/adapter/toss/TossMarketDataProvider.java`에 구현한다. `GET /api/v1/stocks/all?market={KOSPI|KOSDAQ|NYSE|NASDAQ|AMEX}&status=ACTIVE`를 호출해 `SecurityListing`(name→name_ko)으로 바꾸는 `fetchSecurityMaster` 구현이다(T047 통과)

**Checkpoint**: US1 단독으로 계좌·보유 등록 MVP가 동작한다. 현재가 열은 "시세 없음"으로 표시된다.

---

## Phase 4: User Story 2 - 현재가 및 등락 색상 표시 (Priority: P2)

**Goal**: 보유 종목 옆에 현재가·전일 대비 등락·평가손익을 빨강(상승)/파랑(하락)/중립(보합)과 ▲/▼ 기호로 표시하고 1분 이내로 갱신한다.

**Independent Test**: 상승·하락·보합 종목이 하나씩 있을 때 값과 색이 규칙대로인지, 장중 갱신 시각이 바뀌는지, 시세 실패 시 "지연"과 마지막 값이 보이는지, 장 마감 표시를 확인한다(quickstart S3).

### Tests for User Story 2 ⚠️

- [X] T061 [P] [US2] `Quote` 파생값 단위 테스트를 `UT/market/domain/QuoteTest.java`에 작성한다:
  - 70,000 → 71,000이면 change +1,000, rate +1.43%, UP
  - $190.00 → $187.50이면 −2.50, −1.32%, DOWN
  - 같으면 FLAT
  - 신선도: OPEN이고 `now − fetched_at > 1분`이면 STALE(SC-003), 시세 행 없으면 UNAVAILABLE
- [X] T062 [P] [US2] `HoldingValuation` 단위 테스트를 `UT/portfolio/domain/HoldingValuationTest.java`에 작성한다: 평단가 71,500 × 10주, 현재가 71,000이면 평가손익 −5,000, 수익률 −0.70%, DOWN. USD 소수 수량 케이스도 포함한다
- [X] T063 [P] [US2] `MarketCalendar` 단위 테스트를 `UT/market/domain/MarketCalendarTest.java`에 작성한다:
  - KR 09:00~15:30 KST
  - US 09:30~16:00 America/New_York, 서머타임 시작·종료 주 포함
  - 토스증권 장 운영일(오늘이 영업일이 아니면) HOLIDAY
  - 개장 전 PRE_OPEN, 마감 후 CLOSED
- [X] T064 [P] [US2] 토스증권 시세 어댑터 통합 테스트를 `IT/market/TossMarketDataProviderIT.java`에 작성한다(WireMock `GET /api/v1/prices`, `/api/v1/candles?interval=1d`, `/api/v1/market-calendar/{KR|US}`):
  - 국내·해외 현재가 응답 파싱 결과가 BigDecimal이고 문자열 그대로 보존됨
  - 한 종목 500 응답은 `Failure(UPSTREAM_ERROR)`, 나머지는 Success
  - 429는 `RATE_LIMITED`
  - 응답의 거래정지 표시가 있으면 결과에 `suspended=true`(Edge Case 거래정지)
  - 초당 15건 제한 준수
- [X] T065 [P] [US2] `QuoteRefreshScheduler` 통합 테스트를 `IT/market/QuoteRefreshSchedulerIT.java`에 작성한다:
  - 장중 시장의 보유 종목만 호출
  - 실패 종목은 기존 값과 `fetched_at` 유지
  - CLOSED 시장은 마감 후 1회만 종가 확정
  - 보유하지 않은 종목은 호출하지 않음
- [X] T066 [P] [US2] 시세 프래그먼트 계약 테스트를 `IT/market/QuoteFragmentIT.java`에 작성한다:
  - `GET /fragments/quotes`가 200을 반환하고 각 셀에 `hx-swap-oob`, `class="up|down|flat"`, ▲/▼/– 기호가 있음
  - "HH:mm:ss 기준", "지연 (HH:mm 기준)", "시세 없음", "장 마감", "거래정지" 상태 문구
  - 미인증 401
- [X] T067 [P] [US2] E2E 시나리오를 `E2E/market/QuoteDisplayE2E.java`에 작성한다:
  - WireMock 시세로 상승·하락·보합 3종목 구성
  - 색상 CSS 변수와 기호 확인
  - 시세 실패 후 "지연" 표시 확인
  - axe 위반 0건, 색 대비 검사 포함

### Implementation for User Story 2

- [X] T068 [US2] Flyway `RES/db/migration/V5__quote.sql`을 작성한다:
  - `quote`: security_id BIGINT PK FK, price NUMERIC(19,4) NOT NULL, prev_close NUMERIC(19,4) NOT NULL, market_state VARCHAR(12) NOT NULL `CHECK (market_state IN ('PRE_OPEN','OPEN','CLOSED','HOLIDAY'))`, as_of TIMESTAMPTZ NOT NULL, fetched_at TIMESTAMPTZ NOT NULL
  - (휴장일 테이블 없음: 토스증권 장 운영일 API 사용, data-model.md)
- [X] T069 [P] [US2] `Quote`(파생 change, changeRate, direction, freshness)와 `MarketState`를 `MAIN/market/domain/`에 구현한다(T061 통과)
- [X] T070 [P] [US2] `MarketCalendar`를 `MAIN/market/domain/MarketCalendar.java`에 구현한다. 입력은 `Clock`과 토스증권 장 운영일 응답(오늘·직전·다음 영업일)이다(T063 통과)
- [X] T071 [P] [US2] `HoldingValuation`을 `MAIN/portfolio/domain/HoldingValuation.java`에 구현한다. costBasis, marketValue, unrealizedPnl, returnRate(HALF_UP 4자리), direction을 계산한다(T062 통과)
- [X] T072 [US2] `TossMarketDataProvider`에 `fetchQuotes`(`GET /api/v1/prices`, 최대 200종목씩), `fetchMarketCalendar`, `fetchPreviousClose`(일봉 1d로 직전 영업일 종가, 종목당 하루 1회 캐시)를 `MAIN/market/adapter/toss/TossMarketDataProvider.java`에 구현한다. `MARKET_DATA`·`MARKET_DATA_CHART`·`MARKET_INFO` 그룹 레이트 리미터(`TossRateLimiter`)를 거친다. 거래정지 여부(현재가 API에 없음 — `GET /api/v1/stocks/{symbol}/warnings` 응답으로 판별, 구현 시 스펙 확인)는 `QuoteRefreshScheduler`가 `security.trading_status`를 `SUSPENDED`/`ACTIVE`로 갱신하는 데 쓴다(T064 통과)
- [X] T073 [US2] `QuoteRepository`(JdbcClient upsert)를 `MAIN/market/persistence/`에, 장 운영일 캐시(하루 1회 갱신)를 `MAIN/market/application/MarketCalendarCache.java`에 구현한다
- [X] T074 [US2] `QuoteRefreshScheduler`를 `MAIN/market/application/QuoteRefreshScheduler.java`에 구현한다. `@Scheduled(fixedDelayString="${app.quote.refresh-interval}")`, 보유 종목 distinct 조회, 장 상태별 호출 여부를 판단하고 실패 시 기존 행을 유지한다(T065 통과)
- [X] T075 [US2] `DashboardJdbcQuery`에 `LEFT JOIN quote`를 추가하고 `DashboardView` 행에 Quote·HoldingValuation을 채운다. 파일은 `MAIN/portfolio/persistence/DashboardJdbcQuery.java`이며, 단일 쿼리를 유지한다(T045 재통과)
- [X] T076 [US2] 시세 셀 프래그먼트 `RES/templates/fragments/quote-cell.html`을 만든다:
  - 현재가, 전일 대비, 평가손익·수익률, 상태 문구
  - `class` up/down/flat
  - `aria-label`에 "상승/하락/보합" 텍스트 포함
- [X] T077 [US2] `QuoteFragmentController`(`GET /fragments/quotes`)를 `MAIN/market/web/`에 구현하고, `holding-row.html`에 `quote-cell` 포함과 `hx-trigger="every 30s"` 폴링을 연결한다(T066·T067 통과)

**구현 메모 (2026-11-05)**: 계획과 달라진 점
- `quote`에 `session_date`(시세가 속한 거래일) 컬럼을 추가했다. 전일 종가를 거래일이 바뀔 때만 다시 받기 위해서다(종목당 하루 1회 캐시).
- T072 거래정지: `warnings` API에는 거래정지 정보가 없어 `GET /api/v1/stocks`의 `koreanMarketDetail.krxTradingSuspended`를 쓴다(STOCK 그룹, 초당 2회로 제한). 미국 종목은 정지 정보가 없다.
- T073 `QuoteRepository` 대신 포트 `market.application.port.QuoteStore`와 구현 `market.persistence.JdbcQuoteStore`로 나눴다.
- T076 `aria-label` 대신 숨김 텍스트("전일 대비 상승", "손실")를 쓴다. axe가 역할 없는 `span`의 `aria-label`을 위반으로 보기 때문이다. 장 시작 전 상태 문구 "장 시작 전"을 추가했다.
- T077 `QuoteFragmentController`는 `portfolio/web`에 두었다(평가손익 셀도 함께 그리며, market → portfolio 역방향 의존을 피함). 폴링은 행마다가 아니라 대시보드의 `#quote-poller` 하나가 30초마다 OOB 응답을 받는다.

**Checkpoint**: US1 + US2가 함께 동작하며, US2는 시세 픽스처만으로 독립 검증할 수 있다.

---

## Phase 5: User Story 4 - AI 공급자 키 등록과 선택 (Priority: P3)

**Goal**: 소유자가 Claude·OpenAI API 키를 검증 후 암호화 저장하고, 키가 있는 공급자 중 가이드용 공급자를 선택한다.

**Independent Test**: 잘못된 키는 거부되고 입력란이 비워지는지, 올바른 키는 `****abcd`로 표시되고 자동 선택되는지, 두 번째 키 등록 후 선택 변경과 키 삭제 시 선택이 정리되는지, 로그에 키 원문이 없는지 확인한다(quickstart S4).

> US3(가이드)이 최소 1개의 키를 필요로 하므로 US4를 US3보다 먼저 구현한다. 둘 다 P3이다.

### Tests for User Story 4 ⚠️

- [X] T078 [P] [US4] `CredentialCipher` 단위 테스트를 `UT/aicredential/application/CredentialCipherTest.java`에 작성한다: `ApiKey` 암호화 결과의 last4 정확성, 왕복 복호화, `EncryptedSecret`에 평문 없음(공통 `AesGcmSecretCipher`는 T021에서 검증됨)
- [X] T079 [P] [US4] `ApiKey` 값 객체 단위 테스트를 `UT/aicredential/domain/ApiKeyTest.java`에 작성한다: `toString()`은 `****1234`, 빈 값과 공백은 거부, `equals`가 원문을 노출하지 않음
- [X] T080 [P] [US4] 공급자 어댑터 키 검증 통합 테스트를 `IT/guide/AiProviderVerifyKeyIT.java`에 작성한다(WireMock으로 Anthropic·OpenAI 엔드포인트 대역): 200은 VALID, 401은 INVALID, 타임아웃·503은 UNAVAILABLE
- [X] T081 [P] [US4] AI 설정 라우트 계약 테스트를 `IT/aicredential/AiSettingsRoutesIT.java`에 작성한다:
  - `PUT /settings/ai/credentials/{provider}`: 200 / 422 "키를 확인할 수 없습니다"(입력란 비움) / 503
  - 같은 공급자로 재등록하면 교체
  - 첫 키 등록 시 자동 선택
  - `PUT /settings/ai/selection`에 키 없는 공급자는 422
  - `DELETE`는 확인 후 삭제하고 선택을 다른 키로 바꾸거나 NULL로 둠
  - DB `ai_credential`에 평문이 없음(바이트 검색)
  - 미인증 401, CSRF 없음 403
- [X] T082 [P] [US4] 키 비노출 테스트를 `IT/aicredential/ApiKeyLeakIT.java`에 작성한다(SC-009). 등록·검증 실패·가이드 실패 흐름 동안 로그(`OutputCaptureExtension`), 응답 본문, 리다이렉트 URL, DB 모든 텍스트 컬럼에서 키 원문 검색 결과가 0건이어야 한다
- [X] T083 [P] [US4] E2E 시나리오를 `E2E/aicredential/AiSettingsE2E.java`에 작성한다. quickstart S4 1~4단계(WireMock AI 대역)와 axe 위반 0건을 검증한다

### Implementation for User Story 4

- [X] T084 [US4] Flyway `RES/db/migration/V6__ai_credential.sql`을 작성한다:
  - `ai_credential` 테이블: id BIGINT PK, provider VARCHAR(20) NOT NULL UNIQUE `CHECK (provider IN ('ANTHROPIC','OPENAI'))`, ciphertext BYTEA NOT NULL, iv BYTEA NOT NULL `CHECK (octet_length(iv) = 12)`, key_version SMALLINT NOT NULL, last4 CHAR(4) NOT NULL, verified_at TIMESTAMPTZ NOT NULL, created_at/updated_at
- [X] T085 [P] [US4] 도메인과 포트를 만든다:
  - `AiProvider` enum(ANTHROPIC, OPENAI, 표시명 포함), `ApiKey`, `EncryptedSecret`, `KeyCheck`: `MAIN/aicredential/domain/`
  - T079을 통과시킨다
- [X] T086 [P] [US4] `CredentialCipher`를 `MAIN/aicredential/application/CredentialCipher.java`에 구현한다. 공통 `SecretCipher`(T038)를 감싸 `ApiKey` ↔ `EncryptedSecret`·last4를 변환한다(T078 통과)
- [X] T087 [US4] `AiGuideProvider` 포트를 `MAIN/guide/application/port/AiGuideProvider.java`에 정의한다. `provider()`, `verifyKey(ApiKey)`, `generateGuide(ApiKey, GuidePrompt)`를 두고 `GuideOutcome` sealed 타입을 쓴다(contracts/ports.md)
- [X] T088 [US4] `AiGuideClientFactory`와 두 어댑터를 `MAIN/guide/adapter/ai/`에 구현한다:
  - `AiGuideClientFactory`: 요청마다 사용자 키로 `AnthropicChatModel`/`OpenAiChatModel`을 생성하고, 모델명은 `app.ai.models.*`, 타임아웃은 `app.ai.timeout`(60s)
  - `AnthropicGuideProvider`, `OpenAiGuideProvider`
  - 이 단계에서는 `verifyKey`만 구현한다(최소 토큰 요청). 401/403은 INVALID, 타임아웃/5xx는 UNAVAILABLE이다(T080 통과)
  - Spring AI 모델 자동 구성은 `spring.autoconfigure.exclude`로 끈다
- [X] T089 [US4] `AiCredentialEntity`·`AiCredentialRepository`를 `MAIN/aicredential/persistence/`에, `CredentialService`를 `MAIN/aicredential/application/CredentialService.java`에 구현한다:
  - 등록: 검증 성공 시에만 암호화 저장하고, 기존 키가 있으면 교체한다
  - 첫 키면 `owner.selected_ai_provider`를 설정한다
  - 삭제 시 선택을 정리한다
  - `selectProvider`는 키가 있는 공급자만 허용한다
- [X] T090 [US4] `AiSettingsController`(`GET /settings/ai`, `PUT /settings/ai/credentials/{provider}`, `DELETE /settings/ai/credentials/{provider}`, `PUT /settings/ai/selection`)를 `MAIN/aicredential/web/`에, 템플릿을 `RES/templates/settings/ai.html`에 구현한다:
  - 키 입력은 `type="password"`, `autocomplete="off"`이고 오류 재렌더링 시 값을 비운다
  - 키 없는 공급자에는 "키 필요"를 표시하고 선택할 수 없게 한다
  - T081·T083을 통과시킨다
- [X] T091 [US4] `RES/logback-spring.xml`에 마스킹 패턴(`sk-[A-Za-z0-9_-]{8,}`, `sk-ant-[A-Za-z0-9_-]{8,}` → `****`)을 추가한다. `CredentialService` 경계에서 예외를 감쌀 때 원인 메시지에서 키를 제거한다(T082 통과)

**구현 메모 (2026-11-05)**: 계획과 달라진 점
- T085 암호문 타입은 새로 만들지 않고 공통 `common.crypto.EncryptedSecret`을 쓴다. `CredentialCipher.encrypt`는 `EncryptedApiKey(secret, last4)`를 돌려준다.
- T087 `AiGuideProvider`에는 이번 단계에서 쓰는 `provider()`·`verifyKey()`만 두었다. `generateGuide`와 `GuideOutcome`은 US3(T104~)에서 추가한다(호출처 없는 빈 구현을 두지 않기 위해).
- T088 의존성이 스타터가 아닌 `spring-ai-anthropic`·`spring-ai-openai`라 모델 자동 구성이 없으므로 `spring.autoconfigure.exclude`는 필요 없다. 공급자 주소는 `app.ai.base-urls.*`(기본값은 공식 주소)로 바꿀 수 있고, 테스트는 WireMock을 가리킨다.
- T089 JPA 엔티티 대신 다른 기능과 같은 방식인 JdbcClient로 `CredentialStore` 포트와 `JdbcCredentialStore`를 구현했다. V6에 `owner.selected_ai_provider` CHECK 제약을 추가했다.
- T091 키를 로그에 넘기지 않는 것이 1차 규칙이고(`ApiKey.toString()`은 `****1234`, 어댑터는 상태 코드만 기록), `logback-spring.xml`의 `%replace` 마스킹은 메시지와 예외 스택에 모두 적용되는 2차 장치다.
- T082의 "가이드 실패 흐름"은 US3에서 `ApiKeyLeakIT`에 추가한다. T083은 quickstart S4 2~4단계를 다루며, 1단계(가이드 버튼 → 설정 이동)는 US3 E2E에서 다룬다.

**Checkpoint**: AI 키 등록·선택이 단독으로 동작하며, 아직 가이드 버튼은 없다.

---

## Phase 6: User Story 3 - 종목별 AI 투자 가이드 요청 (Priority: P3)

**Goal**: 보유 종목의 가이드 버튼으로 선택한 AI가 주가 흐름과 내 보유 상황을 분석한 가이드를 비동기로 받는다. 종목별 하루 2회, 실패 시 차감 없음, 동시 요청에도 한도를 지킨다.

**Independent Test**: 삼성전자 가이드 2회 성공 후 3회째는 비활성과 초기화 안내가 나오는지, AAPL은 여전히 2회인지, AI 실패 시 횟수가 유지되는지, 동시 요청에서 생성이 2건을 넘지 않는지 확인한다(quickstart S5).

### Tests for User Story 3 ⚠️

- [X] T092 [P] [US3] `GuideRequest` 상태 전이 단위 테스트를 `UT/guide/domain/GuideRequestTest.java`에 작성한다:
  - 허용: PENDING → SUCCEEDED, PENDING → FAILED
  - 종료 상태에서 전이하면 예외(FR-028)
  - `usage_date`는 요청 시작 시각의 KST 날짜(23:59:59.9 KST 요청은 그날)
- [X] T093 [P] [US3] `GuideQuota` 단위 테스트를 `UT/guide/domain/GuideQuotaTest.java`에 작성한다: 남은 횟수 = 2 − 비실패 건수, 하한 0. 초기화 시각은 다음 날 00:00 KST다
- [X] T094 [P] [US3] `GuidePromptBuilder` 단위 테스트를 `UT/guide/domain/GuidePromptBuilderTest.java`에 작성한다:
  - 60거래일 종가로 20/60일 이동평균, 최고·최저, 일간 수익률 표준편차 계산
  - 평단가·수량·평가손익·수익률 포함
  - **계좌명은 포함하지 않음**
- [X] T095 [P] [US3] 슬롯 예약 통합 테스트를 `IT/guide/GuideSlotReservationIT.java`에 작성한다:
  - slot 1 → slot 2 → 세 번째는 `QuotaExceeded`
  - FAILED 처리 후 해당 슬롯 재사용 가능
  - 다른 종목은 독립
  - 같은 종목을 여러 계좌에 보유해도 합산
  - 자정 경계: `MutableClock` 23:59 KST 요청 → 다음 날 00:00 이후에도 2회 사용 가능
- [X] T096 [P] [US3] 동시성 통합 테스트를 `IT/guide/GuideQuotaConcurrencyIT.java`에 작성한다(FR-021, SC-006). 같은 종목에 동시 요청 20건(가상 스레드, `CountDownLatch`)을 보내고, 100회 반복해 비실패 행이 항상 2개 이하인지 확인한다
- [X] T097 [P] [US3] 가이드 생성 어댑터 통합 테스트를 `IT/guide/AiGenerateGuideIT.java`에 작성한다(WireMock):
  - 스키마에 맞는 JSON이면 `Generated(GuideContent)`
  - 스키마 불일치면 `Generated(rawText)`
  - 401·402·잔액 부족은 `CredentialError`
  - 60초 타임아웃·429·5xx는 `TransientError`
  - 응답 모델명 추출
- [X] T098 [P] [US3] 가이드 라우트 계약 테스트를 `IT/guide/GuideRoutesIT.java`에 작성한다:
  - `POST /holdings/{id}/guides`: 202(진행 프래그먼트 `hx-trigger="every 2s"`) / 409+`HX-Redirect: /settings/ai`(키 없음) / 429(한도, "내일 00:00 (KST)") / 409(거래정지·상장폐지) / 404
  - PENDING 중 재요청은 기존 요청 프래그먼트 202(새 슬롯 없음)
  - `GET /guides/{id}/status`: PENDING / SUCCEEDED(`HX-Trigger: guide-quota-changed`) / FAILED(failure_kind별 문구)
  - 실패 시 남은 횟수 유지
  - 미인증 401, `GET /guides/{id}` 없는 id 404
- [X] T099 [P] [US3] `StaleGuideSweeper` 통합 테스트를 `IT/guide/StaleGuideSweeperIT.java`에 작성한다: 3분 넘은 PENDING은 FAILED(`TRANSIENT_ERROR`)가 되고 슬롯이 반환된다
- [X] T100 [P] [US3] 응답 시간 예산 테스트를 `IT/guide/GuideRequestLatencyIT.java`에 작성한다. AI 대역 지연을 10초로 둬도 `POST /holdings/{id}/guides`의 응답 시간이 500ms 미만인지 확인한다(헌법 원칙 IV, research R8)
- [X] T101 [P] [US3] E2E 시나리오를 `E2E/guide/GuideRequestE2E.java`에 작성한다:
  - quickstart S5 1~4단계(WireMock AI): 결과에 요약·진단·행동·근거·위험·면책 문구·"OpenAI · <모델>" 표시
  - 버튼 `aria-busy`, 2회 후 비활성
  - axe 위반 0건

- [X] T102 [P] [US3] 일봉 조회 통합 테스트를 `IT/market/TossDailyCandlesIT.java`에 작성한다(WireMock `GET /api/v1/candles?interval=1d`): 최신순 응답 정렬, 최대 120봉(200 한도 내), BigDecimal 보존

### Implementation for User Story 3

- [X] T103 [US3] Flyway `RES/db/migration/V7__guide_request.sql`을 작성한다:
  - `guide_request` 테이블:
    - id BIGINT PK
    - security_id FK NOT NULL
    - holding_id FK NULL `ON DELETE SET NULL`
    - account_id FK → account NOT NULL(계좌는 소프트 삭제이므로 행 유지, FR-027 "삭제된 계좌" 판별)
    - usage_date DATE NOT NULL
    - slot SMALLINT NOT NULL `CHECK (slot IN (1,2))`
    - status VARCHAR(10) NOT NULL `CHECK (status IN ('PENDING','SUCCEEDED','FAILED'))`
    - failure_kind VARCHAR(20) NULL `CHECK (failure_kind IN ('CREDENTIAL_ERROR','TRANSIENT_ERROR','INVALID_STATE'))`
    - provider VARCHAR(20) NOT NULL, model VARCHAR(80) NULL
    - account_name_snapshot VARCHAR(30) NOT NULL
    - price_snapshot NUMERIC(19,4) NOT NULL, avg_price_snapshot NUMERIC(19,4) NOT NULL
    - quantity_snapshot NUMERIC(20,6) NOT NULL, return_rate_snapshot NUMERIC(9,4) NOT NULL
    - currency CHAR(3) NOT NULL
    - content JSONB NULL, raw_text TEXT NULL
    - requested_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NULL
  - `CREATE UNIQUE INDEX ux_guide_slot ON guide_request (security_id, usage_date, slot) WHERE status <> 'FAILED'`
  - `CREATE INDEX ix_guide_history ON guide_request (security_id, requested_at DESC) WHERE status = 'SUCCEEDED'`
  - 종료 상태(`SUCCEEDED`/`FAILED`) 행의 UPDATE를 거부하는 트리거 함수
- [X] T104 [P] [US3] 가이드 도메인을 `MAIN/guide/domain/`에 구현한다(T092·T093 통과):
  - `GuideRequest`(상태 전이), `GuideStatus`, `FailureKind`, `GuideQuota`
  - `GuideContent` record: summary, diagnosis, action enum `HOLD|BUY_MORE|PARTIAL_SELL|SELL|WATCH`, rationale, risks(1~5개). contracts/ai-guide-output.schema.json과 일치시킨다
- [X] T105 [P] [US3] `GuidePrompt`와 `GuidePromptBuilder`를 `MAIN/guide/domain/`에 구현하고(T094 통과), 시스템 프롬프트 템플릿을 `RES/prompts/guide-system.st`에 둔다. 템플릿은 한국어 응답, JSON 스키마 준수, 투자 자문이 아님을 명시하고 출력 길이에 상한을 둔다
- [X] T106 [US3] `MarketDataProvider.fetchDailyCandles`를 `MAIN/market/adapter/toss/TossMarketDataProvider.java`에 구현한다. `GET /api/v1/candles?interval=1d&count=…`, 최대 120봉이다(T102 통과)
- [X] T107 [US3] `AnthropicGuideProvider`와 `OpenAiGuideProvider`에 `generateGuide`를 구현한다(`MAIN/guide/adapter/ai/`):
  - Spring AI `ChatClient` + `BeanOutputConverter<GuideContent>`
  - 파싱 실패 시 rawText 폴백
  - HTTP 오류를 `CredentialError`/`TransientError`로 분류
  - T097을 통과시킨다
- [X] T108 [US3] `GuideRequestRepository`를 `MAIN/guide/persistence/GuideRequestRepository.java`에 구현한다:
  - `JdbcClient`로 slot 1 INSERT, 유니크 위반(`DuplicateKeyException`)이면 slot 2 INSERT, 또 위반이면 `QuotaExceeded`를 반환한다
  - `markSucceeded`·`markFailed`는 `WHERE status='PENDING'` 조건부로 UPDATE한다
  - `countActive(securityId, date)`, `findPending(securityId, date)`를 둔다
  - T095·T096을 통과시킨다
- [X] T109 [US3] `GuideService.request(holdingId)`를 `MAIN/guide/application/GuideService.java`에 구현한다:
  - 사전 조건 확인: 키 존재, 선택 공급자, 종목 `ACTIVE`, 시세 존재
  - 같은 종목의 PENDING이 있으면 그 요청을 반환한다
  - 스냅샷과 함께 슬롯을 예약하고, `GuideRunner`에 제출한다
- [X] T110 [US3] `GuideRunner`를 `MAIN/guide/application/GuideRunner.java`에 구현한다:
  - `guideExecutor` 가상 스레드에서 실행
  - 일봉 조회 → 프롬프트 생성 → 키 복호화 → `generateGuide`
  - 결과에 따라 `markSucceeded`(content/raw_text, model) 또는 `markFailed`(failure_kind)
  - 예외는 로깅 후 FAILED로 처리하고 삼키지 않는다
  - 실행 직전 종목이 `ACTIVE`가 아니면 AI를 호출하지 않고 `FAILED`/`INVALID_STATE`
  - 요청~완료 소요 시간을 Micrometer 타이머 `guide.generation.duration`(태그 provider, outcome)으로 기록한다(SC-005 측정)
- [X] T111 [US3] `StaleGuideSweeper`(`@Scheduled` 1분마다, `requested_at < now − 3m`인 PENDING을 FAILED/`TRANSIENT_ERROR`로)를 `MAIN/guide/application/StaleGuideSweeper.java`에 구현한다(T099 통과)
- [X] T112 [US3] `GuideController`(`POST /holdings/{id}/guides`, `GET /guides/{id}/status`, `GET /guides/{id}`)를 `MAIN/guide/web/GuideController.java`에 구현한다. 템플릿은 `RES/templates/guide/`에 둔다:
  - `progress.html`, `result.html`(면책 문구 `guide.disclaimer` 고정 표시, 공급자·모델 표시, rawText 폴백 표시), `failure.html`, `quota-exhausted.html`
  - T098·T100를 통과시킨다
- [X] T113 [US3] `holding-row.html`에 가이드 버튼을 추가한다:
  - "가이드 (남은 N회)" 표시
  - 한도 소진·거래정지 시 `disabled`와 사유 표시
  - `hx-post`와 `hx-indicator`
  - `guide-quota-changed` 이벤트로 남은 횟수를 다시 렌더링
  - 남은 횟수는 `DashboardJdbcQuery`에 오늘 KST 기준 집계를 LEFT JOIN해 단일 쿼리를 유지한다(T045 재통과)
  - T101를 통과시킨다

**구현 메모 (2026-11-05)**: 계획과 달라진 점
- T108 슬롯 예약은 `DuplicateKeyException`을 잡는 대신 `INSERT … ON CONFLICT (security_id, usage_date, slot) WHERE status <> 'FAILED' DO NOTHING RETURNING id`를 쓴다. 유니크 위반 예외는 PostgreSQL 트랜잭션을 깨뜨리기 때문이다. 구현은 포트 `GuideStore` + `JdbcGuideStore`다.
- T103 종료 상태 불변 트리거는 `holding_id`만 바뀌는 경우를 허용한다. 보유 종목은 물리 삭제되어 FK `ON DELETE SET NULL`이 종료된 행을 UPDATE하기 때문이다(FR-027 기록 유지). 상태·실패 사유·결과 정합성 CHECK 제약을 추가했다.
- T104 `GuideOutcome`은 `guide.domain`에 두었다(`application.port`에는 인터페이스만 둔다는 ArchUnit 규칙).
- T107 `ChatClient` 대신 `ChatModel` + `BeanOutputConverter`를 쓴다(`spring-ai-client-chat` 의존성 불필요). 잔액 부족은 Anthropic 400 "credit balance", OpenAI 429 `insufficient_quota`로 판별한다.
- T105 시스템 프롬프트의 `{format}`은 StringTemplate 대신 문자열 치환으로 채운다(스키마 JSON의 중괄호 때문).
- T096 동시성은 DB 보장을 직접 검증하도록 `GuideStore.reserve`에 20건 × 100회로 확인한다.
- T109 시세가 없으면 409(`NO_QUOTE`)로 안내한다.
- T112·T113 가이드 결과는 대시보드 상단의 `#guide-panel` 한 곳에 보인다. 버튼은 `GET /holdings/{id}/guide-button`으로 다시 그리며, 같은 종목 행만 갱신하도록 `HX-Trigger`에 `guide-quota-changed-{securityId}`도 함께 보낸다(htmx `allowEval=false`라 트리거 필터를 쓸 수 없음). FAILED 응답에도 이벤트를 보낸다.
- US4에서 미룬 "가이드 실패 흐름 키 비노출"(`ApiKeyLeakIT`)과 quickstart S4 1단계(`GuideRequestE2E`)를 이번에 추가했다.

**Checkpoint**: 가이드 요청·한도·실패 처리가 동작하고, 결과가 guide_request에 기록된다.

---

## Phase 7: User Story 5 - 가이드 기록 조회 (Priority: P4)

**Goal**: 종목별 가이드 기록을 최신순으로, 요청 당시 스냅샷과 함께 조회한다. 보유 종목이나 계좌를 삭제해도 기록은 유지된다.

**Independent Test**: 가이드 기록 3건(픽스처)이 최신순으로 보이는지, 평단가를 수정한 뒤에도 당시 값이 유지되는지, 보유 종목 삭제 후 "보유 종료"로 표시되는지 확인한다(quickstart S6).

### Tests for User Story 5 ⚠️

- [ ] T114 [P] [US5] 가이드 기록 조회 통합 테스트를 `IT/guide/GuideHistoryIT.java`에 작성한다:
  - `GET /securities/{id}/guides?page=`가 20건씩 최신순, SUCCEEDED만 반환
  - 평단가 수정 후에도 스냅샷 값 유지
  - holding 삭제 시 `holding_id` NULL이 되고 "보유 종료" 표시
  - 계좌 삭제 시 "삭제된 계좌" 표시
  - DB에서 SUCCEEDED 행을 UPDATE하면 트리거가 거부(FR-028)
  - 빈 상태 문구
  - 미인증 401, 없는 종목 id 404
- [ ] T115 [P] [US5] E2E 시나리오를 `E2E/guide/GuideHistoryE2E.java`에 작성한다. quickstart S6 1~3단계와 axe 위반 0건을 검증한다

### Implementation for User Story 5

- [ ] T116 [US5] `GuideHistoryQuery`를 `MAIN/guide/persistence/GuideHistoryJdbcQuery.java`에 구현한다. `ix_guide_history` 인덱스를 쓰고, LIMIT 20 OFFSET, `holding_id IS NULL`이면 "보유 종료", `account.deleted_at IS NOT NULL`이면 "삭제된 계좌"를 판별하는 단일 쿼리다(guide_request.account_id JOIN)
- [ ] T117 [US5] `GuideHistoryController`(`GET /securities/{id}/guides`)를 `MAIN/guide/web/GuideHistoryController.java`에, 템플릿을 `RES/templates/guide/history.html`에 구현한다:
  - 4가지 상태 구성
  - 각 기록 카드: 요청 시각, 공급자·모델, 스냅샷 값(`#money`), 행동 분류 배지, 상세 링크 `/guides/{id}`
  - `holding-row.html`에 "기록" 링크 추가
  - T114·T115을 통과시킨다

**Checkpoint**: 모든 사용자 스토리가 독립적으로 동작한다.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 성능 예산 검증, 보안, 문서, 전체 품질 게이트

- [ ] T118 [P] 기준 데이터셋 시드를 `perf/seed.sql`에 작성한다(헌법 1.0.1): 계좌 20개, 보유 종목 500건, 가이드 기록 1만 건, 종목·시세 포함
- [ ] T119 [P] k6 스크립트를 `perf/budget.js`에 작성한다:
  - 로그인 세션
  - `GET /`(목록) p95 ≤ 300ms
  - `GET /fragments/quotes` ≤ 300ms
  - `GET /guides/{id}`(단건) ≤ 200ms
  - `POST /accounts/{id}/holdings`(쓰기) ≤ 500ms
  - `GET /securities/search` ≤ 300ms
  - thresholds 위반 시 실패, 기준선 `perf/baseline.json` 대비 20% 회귀 시 실패
- [ ] T120 [P] 프런트엔드 예산 E2E를 `E2E/perf/WebVitalsE2E.java`에 작성한다. 시드 데이터에서 대시보드 LCP ≤ 2.5s, CLS ≤ 0.1, INP ≤ 200ms(보유 종목 저장·가이드 버튼 상호작용, `PerformanceObserver` event timing), 초기 JS(gzip) ≤ 200KB(htmx만), 버튼 클릭 후 100ms 이내 `aria-busy` 표시(헌법 원칙 III)를 확인한다
- [ ] T121 [P] HTML 린트 테스트를 `UT/ui/TemplateLintTest.java`에 작성한다:
  - `RES/templates/**/*.html`에 인라인 `style=` 금지
  - 하드코딩 색상 금지
  - 모든 `<input>`에 연결된 `<label>` 존재(헌법 원칙 III)
- [ ] T122 [P] 보안 헤더를 `SecurityConfig`에 추가한다: CSP `default-src 'self'`, `X-Content-Type-Options`, `Referrer-Policy`, 세션 쿠키 `HttpOnly; Secure; SameSite=Lax`. 검증은 `IT/auth/SecurityHeadersIT.java`에서 한다
- [ ] T123 [P] `README.md`를 작성한다: 개요, 사전 준비(토스증권 client 키·허용 IP 등록, `APP_CRYPTO_KEY` 생성), `docker compose up`, 품질 게이트 명령, 아키텍처 요약. quickstart.md 링크를 포함한다
- [ ] T124 헌법 `.specify/memory/constitution.md` 상단 Sync Impact Report 주석을 제거한다. 남은 TODO("500ms 충돌")는 research R8 비동기 설계로 해소되었다. 목록 상한 예외는 plan.md Complexity Tracking에 기록되어 있다
- [ ] T125 `./gradlew check`를 전체 실행한다(spotless, checkstyle, error prone, test, integrationTest, e2eTest, jacoco verification, dependencyCheck). 실패 항목을 수정해 모두 통과시킨다
- [ ] T126 `docker compose up -d --build` 후 quickstart.md S1~S6 수동 검증과 `docker compose --profile perf run --rm k6`을 실행하고, 결과를 기준선 `perf/baseline.json`에 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존성 없음
- **Foundational (Phase 2)**: Setup 완료 후 진행하며, 모든 스토리를 막는다
- **US1 (Phase 3)**: Foundational 이후 진행한다. MVP다
- **US2 (Phase 4)**: Foundational 이후 진행한다. `holding`·`security` 테이블(T048, T049)과 대시보드(T055, T058)를 확장하므로 실질적으로 US1 이후다
- **US4 (Phase 5)**: Foundational 이후 진행하며 US1·US2와 독립적이다. 병렬 진행 가능하다
- **US3 (Phase 6)**: US1(보유 종목), US2(시세 스냅샷), US4(AI 키·어댑터 T087~T089)에 의존한다
- **US5 (Phase 7)**: US3(guide_request 테이블·기록 생성)에 의존한다. 픽스처로 독립 검증할 수 있다
- **Polish (Phase 8)**: 모든 스토리 완료 후 진행한다

### User Story Dependency Graph

```text
Setup → Foundational ─┬─▶ US1 ──▶ US2 ──┐
                      │                 ├──▶ US3 ──▶ US5 ──▶ Polish
                      └─▶ US4 ──────────┘
```

### Within Each User Story

- 테스트 작업([P])을 모두 먼저 작성하고 실패(Red)를 확인한다
- 마이그레이션 → 도메인 → 영속성 → 애플리케이션 서비스 → 컨트롤러·템플릿 순서로 진행한다
- 체크포인트에서 `./gradlew check`를 통과시킨 뒤 다음 스토리로 넘어간다

### Parallel Opportunities

- Setup: T004~T010 병렬
- Foundational 테스트: T013~T020 병렬. 구현: T025, T026, T031~T034 병렬
- US1 테스트 T040~T046 병렬, 도메인 T050·T051 병렬
- US2 테스트 T061~T067 병렬, 도메인 T069~T071 병렬
- **US4 전체를 US1·US2와 병렬로 진행할 수 있다**(다른 패키지 `aicredential`, `guide/adapter/ai`, 다른 마이그레이션 V6)
- US3 테스트 T092~T101 병렬, 도메인 T104·T105 병렬
- Polish T118~T123 병렬

---

## Parallel Example: User Story 1

```bash
# US1 테스트 동시 작성:
Task: "Account 도메인 단위 테스트 in src/test/java/com/stockmanagement/portfolio/domain/AccountTest.java"
Task: "Holding 도메인 단위 테스트 in src/test/java/com/stockmanagement/portfolio/domain/HoldingTest.java"
Task: "종목 검색 통합 테스트 in src/integrationTest/java/com/stockmanagement/market/SecuritySearchIT.java"
Task: "계좌 라우트 계약 테스트 in src/integrationTest/java/com/stockmanagement/portfolio/AccountRoutesIT.java"
Task: "보유 종목 라우트 계약 테스트 in src/integrationTest/java/com/stockmanagement/portfolio/HoldingRoutesIT.java"

# US1 도메인 동시 구현:
Task: "Security/Market/Exchange in src/main/java/com/stockmanagement/market/domain/"
Task: "Account/Holding in src/main/java/com/stockmanagement/portfolio/domain/"
```

## Parallel Example: US2와 US4 동시 진행

```bash
# 개발자 A (US2): T061~T077  — market/, portfolio/persistence/DashboardJdbcQuery
# 개발자 B (US4): T078~T091  — aicredential/, guide/adapter/ai/, V6 마이그레이션
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup → Phase 2 Foundational(로그인 + 레이아웃 + 품질 게이트)
2. Phase 3 US1 → **STOP and VALIDATE**: quickstart S1·S2, `./gradlew check`
3. 배포·데모: 계좌·보유 종목 관리 도구

### Incremental Delivery

1. US1 → 보유 현황 정리 (MVP)
2. US2 → 현재가·등락 색상·평가손익
3. US4 → AI 키 등록 (US2와 병렬 가능)
4. US3 → AI 가이드 + 하루 한도
5. US5 → 가이드 기록 조회
6. Polish → 성능 예산·보안 헤더·문서·전체 게이트

---

## Notes

- [P] 작업은 다른 파일이고 미완료 작업에 의존하지 않는다
- 모든 금액·수량은 `BigDecimal`/`NUMERIC`이며 `double`/`float`는 금지한다(ArchUnit T013가 강제)
- 시간 판단은 모두 주입된 `Clock`을 쓴다(테스트는 `MutableClock`)
- API 키 원문은 어디에도 남기지 않는다(T082이 회귀 방지)
- 작업 또는 논리 단위마다 Conventional Commits로 커밋한다
