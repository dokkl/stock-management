# Research: 계좌별 주식 보유 관리와 AI 투자 가이드

**Feature**: `001-stock-portfolio-ai-guide` | **Date**: 2026-11-05

사용자가 정한 스택은 Spring Boot 4, Thymeleaf, JDK 25, Gradle, PostgreSQL, Spring AI 2.x, Docker,
Docker Compose다. 이 문서는 그 안에서 열려 있던 결정과 외부 연동 방식을 정리한다.
버전은 2026-11-05에 Maven Central에서 GA 릴리스를 확인했다.

---

## R1. 프레임워크·런타임 버전

- **Decision**: Spring Boot **4.1.2**, Spring AI **2.0.2**(BOM), JDK **25**(LTS), Gradle **9.x**
  (Kotlin DSL, Wrapper 고정).
- **Rationale**: Spring AI 2.0.2의 autoconfigure 모듈이 Spring Boot 4.1.2를 기준으로 빌드되어 있어
  두 버전 조합이 공식적으로 맞는다. 4.2.0은 아직 RC다.
- **Alternatives considered**:
  - Boot 4.0.x: Spring AI 2.0.2와 마이너 버전이 어긋나 호환성 위험이 있다.
  - Boot 4.2.0-RC1 / Spring AI 2.1.0-RC1: GA가 아니다.

## R2. 시세·종목 데이터 제공자 (한국 + 미국)

> 2026-11-05 변경: 한국투자증권(KIS) Open API → **토스증권 Open API**(사용자 요청). 공식 스펙
> `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json`(v1.2.28)을 기준으로 정리했다.

- **Decision**: **토스증권 Open API**(`https://openapi.tossinvest.com`)를 제공자로 쓴다. 제공자는
  `MarketDataProvider` 포트 뒤로 숨겨, 바꿔도 도메인 코드는 바뀌지 않게 한다.
  - 인증: `POST /oauth2/token`(form, `grant_type=client_credentials`, `client_id`, `client_secret`) →
    `access_token`, `expires_in`. refresh token은 없고, **클라이언트당 유효 토큰이 1개**라 재발급 시 이전 토큰이
    즉시 무효가 된다. 따라서 토큰을 DB에 암호화 캐시하고 만료 5분 전에만 재발급한다. 401이면 캐시를 버리고
    한 번 재시도한다.
  - 성공 응답은 `{"result": ...}` 봉투, 오류는 `{"error": {"code", "message", "requestId"}}`이다.
  - 종목 마스터: `GET /api/v1/stocks/all?market={KOSPI|KOSDAQ|NYSE|NASDAQ|AMEX}&status=ACTIVE`가
    거래소 전체 종목(`symbol`, `name`(한글), `securityType`)을 한 번에 준다. 하루 1회 받아 `security`에 적재한다.
    `STOCK_WARRANTS`(신주인수권)는 제외한다. 영문명은 없으므로 `name_en`은 비워 둔다.
  - 현재가: `GET /api/v1/prices?symbols=a,b,…`(최대 200개). 응답은 `symbol`, `timestamp`, `lastPrice`,
    `currency`뿐이며 **전일 종가가 없다**.
  - 전일 종가·일봉(가이드 분석용): `GET /api/v1/candles?symbol=&interval=1d&count=…`(최대 200봉, 최신순).
  - 장 운영일: `GET /api/v1/market-calendar/{KR|US}`가 오늘·직전·다음 영업일을 준다(휴장일 목록 관리 불필요).
  - 호출 한도(초당, 클라이언트 × 그룹): `AUTH` 5, `STOCK_ALL` 1, `MARKET_INFO` 3, `MARKET_DATA` 15,
    `MARKET_DATA_CHART` 20. 공지 없이 조정될 수 있어 어댑터는 이보다 낮게(4/1/2/12/15) 호출한다.
- **Rationale**: 한 제공자로 한국·미국 시세, 일봉, 종목 목록, 장 운영일을 모두 받을 수 있다. 종목 목록이 JSON
  API라 파일 파싱이 필요 없다.
- **Alternatives considered**:
  - 한국투자증권 Open API: 최초 결정이었으나 사용자 요청으로 교체.
  - Yahoo Finance 비공식 엔드포인트: 비공식이라 언제든 막힐 수 있다.
  - Alpha Vantage / Finnhub: 한국 시세가 없거나 부족하다.
- **Consequences**:
  - 토스증권 `client_id`·`client_secret`은 AI 키와 달리 **운영 설정(환경 변수 `TOSS_CLIENT_ID`,
    `TOSS_CLIENT_SECRET`)**으로 받는다. 시세 연동 인프라 설정이므로 FR-023의 "AI 공용 키 없음" 원칙과 충돌하지
    않는다.
  - **허용 IP 등록이 필요하다.** 운영 서버의 공인 IP를 토스증권 WTS(설정 > Open API)에 등록해야 호출된다.
  - 전일 종가는 일봉으로 별도 조회해야 하므로 US2에서 종목당 하루 1회 캐시한다(R3).

## R3. 현재가 갱신 구조 (SC-002: 50종목 2초, FR-012: 1분 주기)

- **Decision**: **서버 측 스케줄러가 시세를 미리 받아 `quote` 테이블에 저장**하고, 화면은 DB만 읽는다.
  - `QuoteRefreshScheduler`가 30초마다 실행되어 장이 열린 시장의 보유 종목 현재가를
    `GET /api/v1/prices`로 갱신한다. 한 번에 최대 200종목이라 보유 500종목도 3회 호출이면 된다.
  - 전일 종가는 `prices`에 없으므로, 장 운영일 API의 직전 영업일 기준으로 일봉 종가를 종목당 하루 1회 받아
    `quote.prev_close`에 저장한다.
  - 보유 목록 화면은 DB의 최신 시세로 즉시 렌더링한다. htmx로 시세 영역 조각을 30초마다 다시 받는다.
  - 장이 닫힌 시장은 하루 1회(마감 후) 종가를 확정 저장하고 더 이상 호출하지 않는다.
  - 시세 호출이 실패하면 기존 값을 유지하고 `fetched_at`을 갱신하지 않는다. 화면은
    `fetched_at`이 1분보다 오래되면 "지연" 상태로 표시한다(FR-013).
- **Rationale**: 화면 요청이 외부 API를 기다리지 않으므로 목록 조회가 원칙 IV의 300ms 예산과
  SC-002를 지킨다. 이전 값을 DB에 남기므로 실패 시 "마지막 값과 시각" 표시가 자연스럽게 된다.
- **Alternatives considered**:
  - 요청 시점 동기 호출: 50종목 × 외부 지연이면 예산을 넘는다.
  - WebSocket 실시간 시세: 1분 지연 허용 요구사항에 비해 과하다.
  - 인메모리 캐시만 사용: 재시작하면 마지막 값이 사라져 FR-013을 지킬 수 없다.

## R4. 사용자별 AI 키로 Spring AI 호출

- **Decision**: Spring AI 자동 구성 `ChatModel` 빈(정적 키)을 **사용하지 않는다**. 대신
  `AiGuideClientFactory`가 요청마다 복호화한 사용자 키로 공급자별 `ChatModel`을 만든다.
  - Anthropic: `AnthropicChatModel` + 사용자 키로 만든 API 클라이언트
  - OpenAI: `OpenAiChatModel` + 사용자 키로 만든 API 클라이언트
  - 공급자 추가는 `AiProviderAdapter` 구현체 하나와 enum 값 하나 추가로 끝난다(FR-024).
  - 응답은 Spring AI의 구조화 출력(`BeanOutputConverter`)으로 `GuideContent` 레코드(요약, 진단,
    행동 가이드, 근거, 위험 요인)에 매핑한다. 파싱에 실패하면 원문을 본문으로 저장하고
    `structured=false`로 표시한다.
  - 공급자별 기본 모델은 설정 파일에 둔다(예: `app.ai.models.anthropic`, `app.ai.models.openai`).
  - 타임아웃 60초. 응답 상태 401/403/402·잔액 부족은 `CREDENTIAL_ERROR`, 타임아웃·5xx는
    `TRANSIENT_ERROR`로 분류한다(FR-023e).
- **Rationale**: 키가 요청마다 다르므로 정적 빈으로는 구현할 수 없다. 팩토리를 두면 테스트에서
  가짜 어댑터로 바꾸기 쉽다.
- **Alternatives considered**: 공급자 SDK 직접 호출 — 사용자가 Spring AI를 지정했고, 구조화 출력과
  공급자 추상화를 직접 구현해야 한다.
- **Implementation note**: 키 검증(FR-023a)은 최소 토큰 요청 1회(max tokens 1)로 확인한다.

## R5. AI 키 암호화 저장 (FR-023b, SC-009)

- **Decision**: AES-256-GCM. 마스터 키는 환경 변수 `APP_CRYPTO_KEY`(32바이트, Base64)로 받는다.
  행마다 무작위 12바이트 IV를 쓰고 `key_version` 컬럼을 둬서 마스터 키 교체에 대비한다. DB에는
  암호문, IV, 끝 4자리만 저장한다.
- **Log hygiene**: 키 값을 담는 타입은 `toString()`이 `****1234`를 반환하는 `ApiKey` 값 객체로
  감싼다. 이 원문이 로그에 나오지 않는지 테스트로 검증한다(SC-009).
- **Alternatives considered**:
  - Spring Security `Encryptors.stronger`: 동작은 비슷하지만 비밀번호 기반 키 파생과 솔트 관리가
    추가된다.
  - Vault: 1인용 도구에 운영 부담이 크다.

## R6. 인증 (FR-001, FR-001a)

- **Decision**: Spring Security 폼 로그인과 세션 쿠키. 비밀번호는 Argon2id로 해시한다.
  - 소유자 계정이 없으면 모든 요청을 `/setup`으로 보내 비밀번호를 설정하게 한다(한 번만 가능).
  - 실패 5회 시 15분 잠금은 `owner.failed_attempts`, `owner.locked_until` 컬럼으로 처리한다.
  - 세션 비활성 만료는 7일, CSRF는 켜 둔다(htmx 요청 헤더로 토큰 전달).
- **Alternatives considered**: HTTP Basic은 로그아웃과 잠금 UX가 나쁘다. OAuth는 1인용에 과하다.

## R7. 종목별 하루 2회 한도와 동시성 (FR-018, FR-020, FR-021, SC-006)

- **Decision**: **DB 유니크 제약으로 슬롯을 예약**한다.
  - `guide_request`에 `(security_id, usage_date, slot)` 부분 유니크 인덱스를 둔다. 조건은
    `status <> 'FAILED'`이고, `slot`은 1 또는 2다.
  - 요청 시 slot 1로 INSERT를 시도하고, 충돌하면 slot 2로 시도한다. 둘 다 충돌하면 한도 초과다.
  - AI 호출이 실패하면 상태를 `FAILED`로 바꾼다. 그러면 부분 인덱스에서 빠져 슬롯이 반환된다(FR-020).
  - `usage_date`는 요청 시작 시각을 `Asia/Seoul` 기준 날짜로 바꾼 값이다.
  - `PENDING` 상태가 3분 넘게 남아 있으면 정리 스케줄러가 `FAILED`로 바꾼다(서버 재시작 대비).
- **Rationale**: 여러 탭이나 여러 인스턴스에서 동시에 요청해도 DB가 한도를 보장한다. 락을 오래 잡지
  않는다.
- **Alternatives considered**:
  - `SELECT COUNT … FOR UPDATE`: 행이 없을 때 잠글 대상이 없어 경쟁 상태가 생긴다.
  - 애플리케이션 메모리 락: 인스턴스가 여러 개면 깨진다.

## R8. 가이드 요청 흐름과 성능 예산 (헌법 원칙 IV)

- **Decision**: **비동기 처리**로 한다.
  - `POST /holdings/{id}/guides`가 슬롯을 예약하고 `202 Accepted` + 상태 조각을 바로 반환한다.
    목표는 500ms 미만이다.
  - 실제 AI 호출은 가상 스레드 실행기에서 백그라운드로 돌린다(JDK 25 virtual threads).
  - 화면은 htmx로 `GET /guides/{id}/status`를 2초마다 불러 완료 여부를 확인한다. 완료되면 결과
    조각으로 바뀐다.
- **Rationale**: 웹 요청은 모두 원칙 IV의 응답 예산 안에 들어간다. AI 응답 시간(SC-005, 30초)은
  별도 성공 기준으로 측정한다. 결과적으로 헌법 1.0.1 Sync Impact Report에 적어 둔 "500ms 예산 충돌"이
  해소되므로 Complexity Tracking 예외가 필요 없다.
- **Alternatives considered**: 동기 처리 — 요청이 최대 60초 걸려 예산을 넘고 스레드를 오래 잡는다.

## R9. 프런트엔드 (Thymeleaf)

- **Decision**: Thymeleaf 서버 렌더링 + **htmx 2.x**(약 14KB gzip, WebJar로 직접 제공)로 부분
  갱신. 별도 JS 번들 빌드는 없다.
  - 디자인 토큰은 `static/css/tokens.css`의 CSS 변수로 정의한다. 상승은 `--color-up`(빨강),
    하락은 `--color-down`(파랑), 보합은 `--color-flat`.
  - 공용 컴포넌트는 Thymeleaf 프래그먼트(`fragments/*.html`)로 만든다. 버튼, 폼 필드, 확인
    대화상자, 토스트, 4가지 상태(로딩/빈/오류/정상)를 포함한다.
  - 메시지 카탈로그는 `messages.properties`(한국어 기본)를 쓴다.
  - 숫자·통화 형식은 `MoneyFormatter`와 `#money` Thymeleaf 유틸리티 객체로 통일한다.
- **Rationale**: 원칙 IV의 초기 JS 200KB 예산을 크게 밑돈다. 원칙 III의 일관성은 프래그먼트 재사용으로
  강제한다.
- **Alternatives considered**:
  - React/Vue SPA: 사용자가 Thymeleaf를 지정했다.
  - Alpine.js 추가: 필요해지면 그때 넣는다(YAGNI).

## R10. 영속성과 마이그레이션

- **Decision**:
  - PostgreSQL 18 (Docker 이미지 `postgres:18`)
  - Flyway로 스키마를 관리한다.
  - 소유자(owner)만 Spring Data JPA를 쓰고, 계좌·보유·종목은 읽기·쓰기 모두 `JdbcClient`로 SQL을 직접 쓴다
    (구현 중 결정: 엔티티 매핑 없이 쿼리가 명시적이고, 대시보드 단일 쿼리와 같은 방식으로 통일).
  - 목록·대시보드 조회는 한 번에 조인해 N+1을 원천 차단한다.
- **Money**: 금액은 `NUMERIC(19,4)` ↔ `BigDecimal`, 수량은 `NUMERIC(20,6)` ↔ `BigDecimal`이다.
  `Money(amount, Currency)` 값 객체를 두고 통화별 스케일(KRW 0, USD 2~4)을 검증한다.
- **N+1 guard**: 통합 테스트에서 Hibernate 통계로 화면당 쿼리 수 상한을 검증한다.

## R11. 테스트·품질 도구 (헌법 원칙 I·II, 품질 게이트)

| 목적 | 도구 |
|---|---|
| 단위 테스트 | JUnit 6 (Jupiter), AssertJ, Mockito |
| 통합 테스트 | Spring Boot Test, Testcontainers 2.x (PostgreSQL) |
| 외부 API 대역 | WireMock (토스증권, Anthropic, OpenAI HTTP 응답 재현) |
| 계층 규칙 | ArchUnit (presentation → application → domain 단방향) |
| E2E + 접근성 | Playwright for Java + axe-core 주입 검사 |
| 커버리지 | JaCoCo (전체 라인 80%, `..domain..` 라인 90%·분기 85% 미만이면 빌드 실패) |
| 포맷·린트 | Spotless (palantir-java-format), Checkstyle (복잡도 10, 메서드 50줄), Error Prone |
| 성능 | k6 (Docker Compose `perf` 프로필), 기준 데이터셋 시드 스크립트 |
| 보안 | OWASP Dependency-Check (CVSS ≥ 7 실패), gitleaks |
| 시간·난수 결정성 | `Clock` 빈 주입, 테스트에서 `MutableClock` 사용 |

## R12. 배포·실행 (Docker, Docker Compose)

- **Decision**:
  - 멀티 스테이지 Dockerfile을 쓴다. 빌드는 `eclipse-temurin:25-jdk`, 실행은
    `eclipse-temurin:25-jre`이고, 레이어드 JAR와 non-root 사용자를 쓴다.
  - `compose.yaml` 서비스는 `app`, `db`(postgres:18, 볼륨, healthcheck)이고, 성능 측정용 `k6`는
    `perf` 프로필에 둔다.
  - 로컬 개발은 Spring Boot Docker Compose 지원(`spring-boot-docker-compose`)으로 `db`를 자동 기동한다.
  - 비밀값(`APP_CRYPTO_KEY`, `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET`, DB 비밀번호)은 `.env`로 주입하고,
    `.env`는 커밋하지 않는다(`.env.example`만 커밋).
