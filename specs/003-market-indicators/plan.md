# Implementation Plan: 대시보드 시장 지표 요약

**Branch**: `015-dashboard-footer` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-market-indicators/spec.md`

## Summary

대시보드 상단에 8개 시장 지표(코스피·코스닥·원달러 환율·나스닥 종합·S&P 500·다우·WTI·VIX)를 카드로 보여 준다.
국내 지수·환율은 토스증권 Open API(고정 IP 프록시 경유)에서 장중 1분마다, 미국 지표는 FRED API에서 1시간마다 받아 DB에
지표별 최신 1행으로 저장한다. 화면은 저장값만 읽고(htmx 60초 폴링), 외부 호출은 스케줄러만 한다. 사용자 범위가 없는
공유 데이터다. 하단 저작권 문구는 구현 완료.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1(Web MVC, Thymeleaf, Scheduling), htmx 2, RestClient(JDK HttpClient)

**Storage**: PostgreSQL 18, Flyway V12(`market_indicator_quote`)

**Testing**: JUnit 5, ArchUnit, Testcontainers, WireMock(토스·FRED 대역), Playwright + axe

**Target Platform**: Linux 컨테이너(AWS Lightsail 컨테이너 서비스)

**Project Type**: 서버 렌더링 웹 앱(단일 프로젝트)

**Performance Goals**: 대시보드 응답에 지표 조회 1쿼리 추가(쿼리 수 상한 갱신), 기존 성능 예산 유지

**Constraints**: 화면 요청 경로에서 외부 호출 금지. 토스 그룹 한도(`MARKET_INDICATOR`, `MARKET_INFO`) 이하. FRED 120회/분 이하.

**Scale/Scope**: 지표 8개 고정, 사용자 수와 무관한 호출량(분당 토스 3~4회, 시간당 FRED 5회)

## Constitution Check

| 원칙 | 판단 |
|---|---|
| I. 코드 품질 | `market` 패키지 안에서 web → application → domain, 외부 연동은 port 인터페이스 + adapter. 도메인 금액·지수는 `BigDecimal`. |
| II. 테스트 표준 | 도메인(등락·상태 계산) 단위 테스트(분기 85%+), 어댑터 WireMock IT(토스·FRED, 결측치 `.`), 라우트 IT(성공·미인증), E2E(표시·axe). TDD. 시각은 주입 `Clock`. |
| III. UX 일관성 | 토큰·공용 프래그먼트만, 등락은 ▲/▼/– + 숨김 텍스트, 숫자 표기는 `#money` 계열 포매터, 4상태(loading/empty/error/ready), 메시지 카탈로그. |
| IV. 성능 | 화면은 DB 1쿼리, 외부 호출은 스케줄러. `QueryCounter` 상한 갱신. |

위반 없음. Complexity Tracking 불필요.

## Project Structure

### Documentation (this feature)

```text
specs/003-market-indicators/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── web-routes.md
│   └── external-apis.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/com/stockmanagement/market/
├── domain/        MarketIndicator(enum), IndicatorQuote, IndicatorStatus
├── application/   MarketIndicatorRefresher(@Scheduled), MarketIndicatorQuery
│   └── port/      IndicatorStore, DomesticIndicatorSource, GlobalIndicatorSource
├── adapter/
│   ├── toss/      TossIndicatorSource(시장 지표 현재가·일봉, 환율)
│   └── fred/      FredIndicatorSource, FredProperties
├── persistence/   JdbcIndicatorStore
└── web/           MarketIndicatorController(GET /fragments/indicators)

src/main/resources/
├── db/migration/V12__market_indicator_quote.sql
├── templates/market/indicators.html
└── messages.properties, static/css/app.css

infra/terraform/parameters.tf   # FRED_API_KEY (SSM)
```

**Structure Decision**: 기존 `market` 기능 패키지에 넣는다(종목·시세와 같은 공유 시장 데이터). 대시보드는 `portfolio` 템플릿에서
지표 프래그먼트를 포함만 하고, 데이터는 `market.web` 컨트롤러와 대시보드 모델 속성으로 공급한다.

## Complexity Tracking

없음.
