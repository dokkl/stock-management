# CLAUDE.md

1인 소유자용 주식 포트폴리오 웹 앱. Java 25 + Spring Boot 4.1 + Thymeleaf/htmx + PostgreSQL 18.
사람용 개요는 `README.md`에 있다.

## 먼저 읽을 것

- `.specify/memory/constitution.md`: 프로젝트 헌법. 다른 모든 관행보다 우선한다.
- `specs/001-stock-portfolio-ai-guide/`: 현재 기능의 `spec.md`, `plan.md`, `data-model.md`,
  `contracts/`, `tasks.md`. 구현 전에 해당 작업(Txxx)과 계약 문서를 확인한다.
- `tasks.md`의 체크박스(`[X]`/`[ ]`)가 진행 상황의 기준이다. 작업을 끝내면 체크한다.

## 명령어

```bash
./gradlew spotlessApply          # 커밋 전 포맷 적용 (spotlessCheck가 check에 포함됨)
./gradlew test                   # 단위 + ArchitectureTest (Docker 불필요, 빠름)
./gradlew integrationTest        # Testcontainers 사용, Docker 필요
./gradlew e2eTest                # Playwright, Docker 필요
./gradlew test --tests 'com.stockmanagement.portfolio.domain.*'   # 일부만 실행
./gradlew check                  # CI와 동일한 전체 게이트
./gradlew bootRun                # db는 spring-boot-docker-compose가 compose.yaml로 자동 기동
```

## 빌드가 깨지기 쉬운 지점

- `javac -Xlint:all -Werror`와 Error Prone이 켜져 있다. 경고 하나도 빌드 실패다.
- Checkstyle `maxWarnings = 0`: 메서드 순환 복잡도 10 이하, 길이 50줄 이하.
- JaCoCo: 전체 라인 80%, `com.stockmanagement.*.domain*` 라인 90%·분기 85%. 도메인 코드를 추가하면
  분기까지 테스트한다.
- `spring.jpa.hibernate.ddl-auto: validate`: 엔티티를 바꾸면 Flyway 마이그레이션
  (`src/main/resources/db/migration/V{n}__*.sql`)을 새로 추가한다. 이미 있는 마이그레이션은 고치지 않는다.

## 아키텍처 규칙 (ArchitectureTest가 강제)

- 기능 패키지: `auth`, `portfolio`, `market`, `aicredential`, `guide`(US3에서 확장), 공용은 `common`.
- 각 기능 안에서 `web → application → domain` 한 방향. `domain`은 domain 바깥에 의존하지 않는다.
- `web`은 `persistence`/`adapter`에 의존하지 않는다. `application`은 `web`에 의존하지 않는다.
- 외부 연동·영속성은 `application/port`의 **인터페이스**를 `persistence/` 또는 `adapter/`에서 구현한다.
- domain에 `double`/`float` 필드 금지. 금액은 `common.domain.Money`(`BigDecimal`), DB는 `NUMERIC`.

## 코딩 규칙

- TDD: 실패하는 테스트 먼저. 버그 수정은 재현 테스트부터.
- 테스트 결정성: 현재 시각은 주입된 `Clock`으로만 얻는다(`LocalDate.now()` 직접 호출 금지).
  외부 HTTP(토스증권, AI)는 WireMock으로 대체한다. 통합 테스트는 `support/IntegrationTestBase`,
  `TestClockConfig`를 쓴다.
- 날짜 기준 시간대는 `Asia/Seoul`(`app.zone`, `KstDate`).
- 통합 테스트는 라우트마다 성공·검증 실패·미인증·404 케이스를 포함한다. 화면별 쿼리 수 상한은
  `QueryCounter`로 검증한다(N+1 금지).
- 화면: 사용자 문자열은 `messages.properties`, 스타일은 `static/css/tokens.css` 토큰과
  `templates/fragments/` 공용 프래그먼트만 사용한다. 인라인 `style` 금지. 금액 표기는 `#money`
  (`MoneyDialect`/`MoneyFormatter`)로만 한다.
- 데이터 화면은 loading/empty/error/ready 네 상태를 모두 구현한다(`fragments/states.html`).
- 등락은 색만으로 표시하지 않고 ▲/▼ 기호를 함께 쓴다(WCAG 2.1 AA, E2E에서 axe 검사).
- 비밀값: AI 키는 `SecretCipher`(AES-GCM, `APP_CRYPTO_KEY`)로 암호화해 저장하고, 원문을 로그·화면·DB에
  남기지 않는다. `.env`는 커밋하지 않는다.

## 워크플로

- 기능 작업은 Spec Kit 스킬(`/speckit-specify` → `clarify` → `plan` → `tasks` → `implement`)을 따른다.
- 커밋 메시지는 Conventional Commits(`feat:`, `fix:`, `test:`, `refactor:`, `docs:`).
- 기능 브랜치 + PR로 진행한다. `main`에 직접 커밋하지 않는다.

## 참고

- 성능 예산: `perf/seed.sql`(기준 데이터셋) + `perf/budget.js`(k6) + `perf/baseline.json`(기준선, 20% 회귀 시 실패).
  k6는 `noCookiesReset: true`가 없으면 반복마다 세션이 사라진다. 프런트엔드 예산은 `WebVitalsE2E`.
- 제품 한도: 계좌 최대 2개(`app.portfolio.max-accounts`), 보유 종목 최대 20개(`app.portfolio.max-holdings`).
- 토스증권 API는 그룹별 호출 한도가 있어 `TossRateLimiter`가 그보다 낮게 제한한다. 호출을 추가할 때
  해당 그룹 한도를 확인한다.
