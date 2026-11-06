# Implementation Plan: 계좌별 주식 보유 관리와 AI 투자 가이드

**Branch**: `001-stock-portfolio-ai-guide` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-stock-portfolio-ai-guide/spec.md`

## Summary

소유자 1인이 쓰는 주식 포트폴리오 웹 앱이다. 계좌별로 한국·미국 보유 종목(평단가, 수량)을 등록하면,
전일 대비 등락을 빨강/파랑으로 구분한 현재가와 평가손익을 보여준다. 종목마다 소유자 본인의 AI 키
(Claude, OpenAI 등)로 가이드를 받을 수 있다. 가이드는 종목별로 하루 2회까지이며, 결과는 요청 당시
스냅샷과 함께 영구 기록된다.

기술 접근:
- **화면**: Spring Boot 4.1 + Thymeleaf 서버 렌더링. htmx로 부분 갱신한다.
- **시세**: 토스증권 Open API를 스케줄러가 미리 받아 DB에 저장하고, 화면은 DB만 읽는다.
- **AI 호출**: Spring AI 2.0 `ChatModel`을 요청마다 사용자 키로 만든다. 가이드는 비동기로 처리한다
  (202 + 폴링).
- **하루 한도**: PostgreSQL 부분 유니크 인덱스로 슬롯을 예약해 동시성까지 DB가 보장한다.

## Technical Context

**Language/Version**: Java 25 (LTS)

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Thymeleaf, Security, Data JPA, Validation,
Docker Compose support), Spring AI 2.0.2 (`spring-ai-anthropic`, `spring-ai-openai`), Flyway, htmx 2.x
(WebJar)

**Storage**: PostgreSQL 18 (`pg_trgm` 확장)

**Testing**: JUnit 6, AssertJ, Mockito, Spring Boot Test, Testcontainers 2.x, WireMock, ArchUnit,
Playwright for Java + axe-core, JaCoCo, k6

**Target Platform**: Linux 컨테이너 (Docker / Docker Compose). 브라우저는 데스크톱 우선이고 모바일
화면에 대응한다.

**Build**: Gradle 9 (Kotlin DSL, Wrapper), 단일 모듈

**Project Type**: web-service (서버 렌더링 웹 앱)

**Performance Goals**:
- 헌법 원칙 IV: 단건 조회 p95 200ms, 목록 p95 300ms, 쓰기 p95 500ms
- 프런트엔드: LCP 2.5s, INP 200ms, 초기 JS 200KB gzip 이하
- SC-002: 50종목 대시보드 2초 이내
- SC-005: 가이드 95%가 30초 이내(웹 검색 포함 시 60초 이내)

**Constraints**:
- 시세는 최대 1분 지연을 허용한다.
- 토스증권 그룹별 호출 한도(예: `MARKET_DATA` 초당 15회, `STOCK_ALL` 초당 1회) 안에서 동작해야 하며, 어댑터가 그룹별로 그보다 낮게 제한한다.
- AI 키 원문을 화면·로그·DB 평문 어디에도 남기지 않는다.
- 하루 기준은 `Asia/Seoul`이다.

**Scale/Scope**:
- 사용자 1명
- 제품 한도(2026-11-05): 소유자 1인당 계좌 최대 2개(FR-002a), 보유 종목 최대 20개(FR-003a)
- 기준 데이터셋(헌법 1.1.0): 계좌 2개, 보유 종목 20건, 가이드 기록 1만 건
- 화면 약 7개(setup, login, 대시보드, 종목 검색/등록, 가이드 결과, 가이드 기록, AI 설정)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

헌법 v1.0.1 기준. **Phase 0 전: PASS / Phase 1 후 재평가: PASS**

| 원칙 / 게이트 | 계획상 준수 방법 | 상태 |
|---|---|---|
| I. 린트·포맷 경고 0 | Spotless, Checkstyle, Error Prone을 `check`에 연결하고 경고는 오류로 처리 | ✅ |
| I. 복잡도 ≤10, 함수 ≤50줄 | Checkstyle `CyclomaticComplexity` 10, `MethodLength` 50 | ✅ |
| I. 도메인 분리·단방향 의존 | `web → application → domain`, `adapter.out → application(port)`. ArchUnit 테스트로 강제 | ✅ |
| I. 금액에 부동소수점 금지 | `BigDecimal` + `Money` 값 객체, DB는 `NUMERIC`. ArchUnit으로 domain의 `double`/`float` 필드 금지 | ✅ |
| I. 경계 검증 | 웹 폼은 Bean Validation + 도메인 생성자 검증. 외부 응답은 어댑터에서 파싱·검증 | ✅ |
| I. 오류 삼킴 금지 | 실패는 `QuoteResult.Failure`, `GuideOutcome`처럼 명시적 결과 타입으로 다룬다. 전역 예외 처리기를 둔다 | ✅ |
| II. TDD | tasks.md에서 각 스토리마다 테스트 작업을 구현 작업보다 먼저 둔다 | ✅ |
| II. 계층별 테스트 | 단위(domain) / 통합(라우트별 성공·검증·인증·404, Testcontainers) / E2E(스토리별 P1 시나리오) | ✅ |
| II. 커버리지 80% / domain 90%·85% | JaCoCo verification 규칙 | ✅ |
| II. 결정성 | `Clock` 주입. 외부 API는 WireMock. 난수는 IV 생성에만 쓰고 테스트는 속성 기반으로 검증 | ✅ |
| III. 디자인 토큰·공용 컴포넌트 | `tokens.css` + Thymeleaf 프래그먼트 라이브러리. 인라인 `style` 금지(Checkstyle 대신 HTML 린트 테스트) | ✅ |
| III. 4가지 상태 | 대시보드·가이드·기록 화면 모두 loading/empty/error/ready 프래그먼트 | ✅ |
| III. 메시지 카탈로그·단일 포매터 | `messages.properties`, `MoneyFormatter` / `#money` | ✅ |
| III. 비가역 작업 확인 | 계좌·보유·키 삭제는 공용 확인 대화상자 프래그먼트 | ✅ |
| III. WCAG 2.1 AA, 색 외 정보 | ▲/▼ 기호 병기, axe 검사 E2E 게이트 | ✅ |
| III. 100ms 피드백 | htmx `hx-indicator`, 버튼 `aria-busy`·비활성 | ✅ |
| IV. API 지연 예산 | 시세는 사전 수집(R3), 조회는 JdbcClient 단일 쿼리, 가이드는 비동기 202(R8) | ✅ |
| IV. 페이지네이션 ≤100 | 가이드 기록 20건/페이지, 검색 최대 20건. 대시보드·시세 조각은 보유 종목 상한 20건(FR-003a)이라 한 번에 보여도 100건 이하 | ✅ |
| IV. N+1 금지 | 화면별 쿼리 수 상한 통합 테스트 | ✅ |
| IV. 자동 성능 테스트 | k6 + 기준 데이터셋 시드, 기준선 대비 20% 회귀 시 실패 | ✅ |
| 게이트 5. 보안 | OWASP Dependency-Check, gitleaks, `.env` 미커밋 | ✅ |
| 워크플로 | 기능 브랜치·PR·리뷰 1인, Conventional Commits | ✅ |

**주석**:
- **대시보드와 "목록 API 페이지네이션"**: 보유 종목 상한이 20건(2026-11-05 변경)이라 대시보드가 전체를 한 번에
  보여도 헌법의 "한 번에 최대 100건"을 지킨다. 이전의 500건 예외는 삭제했다.
- **헌법 1.0.1의 후속 TODO("AI 생성 500ms 충돌")**: R8 비동기 설계로 해소된다. Complexity Tracking
  예외는 필요 없다. 헌법 Sync Impact Report의 TODO는 이 계획을 근거로 닫을 수 있다.

## Project Structure

### Documentation (this feature)

```text
specs/001-stock-portfolio-ai-guide/
├── plan.md              # 이 문서
├── research.md          # Phase 0: 결정 사항 R1~R12
├── data-model.md        # Phase 1: 엔티티·제약·상태 전이
├── quickstart.md        # Phase 1: 실행·검증 가이드
├── contracts/
│   ├── web-routes.md                # 화면·htmx 경로 계약
│   ├── ports.md                     # 외부 연동 포트 계약
│   └── ai-guide-output.schema.json  # AI 출력 스키마
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

기능(feature) 단위 패키지 안에 계층을 둔다.

```text
build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml           # 버전 카탈로그
config/checkstyle/checkstyle.xml
Dockerfile
compose.yaml                        # app, db, (perf) k6
.env.example
perf/                               # k6 스크립트, 기준 데이터셋 시드 SQL

src/main/java/com/stockmanagement/
├── StockManagementApplication.java
├── common/
│   ├── domain/                     # Money, Currency, Direction, KstDate
│   ├── web/                        # GlobalExceptionHandler, HtmxSupport, MoneyDialect(#money)
│   └── config/                     # ClockConfig, AsyncConfig(virtual threads), SecurityConfig
├── auth/
│   ├── domain/                     # Owner, LoginLockPolicy
│   ├── application/                # SetupService, LoginAttemptService
│   └── web/                        # SetupController, LoginController
├── portfolio/
│   ├── domain/                     # Account, Holding, HoldingValuation
│   ├── application/                # AccountService, HoldingService, DashboardQuery, port/
│   ├── persistence/                # JPA entities/repos, DashboardJdbcQuery
│   └── web/                        # DashboardController, AccountController, HoldingController
├── market/
│   ├── domain/                     # Security, Quote, MarketState, MarketCalendar
│   ├── application/                # QuoteRefreshScheduler, SecurityMasterSync, SecuritySearch,
│   │                               #   port/MarketDataProvider
│   ├── persistence/
│   ├── adapter/toss/               # TossMarketDataProvider, TossTokenStore, TossRateLimiter
│   └── web/                        # SecuritySearchController, QuoteFragmentController
├── aicredential/
│   ├── domain/                     # AiProvider, ApiKey, EncryptedSecret
│   ├── application/                # CredentialService, port/CredentialCipher
│   ├── crypto/                     # AesGcmCredentialCipher
│   ├── persistence/
│   └── web/                        # AiSettingsController
└── guide/
    ├── domain/                     # GuideRequest(상태 전이), GuideQuota, GuidePrompt, GuideContent
    ├── application/                # GuideService(예약/실행), GuideRunner(async), StaleGuideSweeper,
    │                               #   port/AiGuideProvider
    ├── persistence/                # GuideRequestRepository(슬롯 INSERT)
    ├── adapter/ai/                 # AnthropicGuideProvider, OpenAiGuideProvider, AiGuideClientFactory
    └── web/                        # GuideController, GuideHistoryController

src/main/resources/
├── application.yml
├── db/migration/                   # Flyway V1__init.sql ...
├── messages.properties
├── prompts/guide-system.st         # 가이드 시스템 프롬프트 템플릿
├── static/css/{tokens.css, app.css}
└── templates/
    ├── layout.html
    ├── fragments/                  # button, field, confirm-dialog, toast, states, quote-cell
    ├── auth/  portfolio/  guide/  settings/

src/test/java/com/stockmanagement/           # 단위 테스트 + ArchitectureTest(ArchUnit)
src/integrationTest/java/com/stockmanagement/ # Testcontainers, WireMock, 라우트 계약, GuideQuotaConcurrencyIT
src/e2eTest/java/com/stockmanagement/         # Playwright + axe, 스토리별 시나리오
```

**Structure Decision**:
- 단일 Gradle 모듈로 한다. Thymeleaf 서버 렌더링이라 프런트엔드를 따로 둘 필요가 없다.
- 패키지는 기능별(auth, portfolio, market, aicredential, guide)로 나누고, 각 기능 안에서
  `web → application → domain` 계층과 `application.port ← adapter/persistence` 의존 역전을 쓴다.
  ArchUnit이 이 규칙을 강제한다.
- 테스트는 Gradle JVM Test Suite로 `test`, `integrationTest`, `e2eTest` 세 소스셋으로 나눈다.
  그래서 빠른 단위 테스트와 컨테이너가 필요한 테스트를 따로 실행할 수 있다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (없음) | 2026-11-05 보유 종목 상한을 20건으로 낮추면서 대시보드 500건 예외가 해소되어 삭제했다 | — |

## Risks & Open Items

| 위험 | 영향 | 대응 |
|---|---|---|
| 토스증권 Open API 사용에 소유자의 토스증권 계정·client 키와 **허용 IP 등록**이 필요 | 키나 IP 등록이 없으면 시세 기능 불가 | `MarketDataProvider` 포트로 분리해 두어 제공자 교체 가능. quickstart 사전 준비에 명시 |
| 토스증권 현재가 API에 전일 종가가 없음 | 등락 색상 계산 불가 | 일봉 API로 직전 영업일 종가를 종목당 하루 1회 조회해 캐시(research R2·R3) |
| Spring AI 2.0 구조화 출력이 공급자마다 스키마를 지키지 않을 수 있음 | 가이드 파싱 실패 | `raw_text` 폴백 저장 + 화면 원문 표시. 실패율은 로그 지표로 관찰 |
| AI 응답 30초 초과(SC-005) | 성공 기준 미달 | 출력 길이 상한, 모델 선택을 설정으로 조정 가능하게 유지 |
