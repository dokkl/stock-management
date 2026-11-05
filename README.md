# Stock Management

소유자 1인이 쓰는 주식 포트폴리오 웹 앱입니다. 계좌별로 한국·미국 보유 종목(평단가, 수량)을 등록하면
현재가, 전일 대비 등락(▲ 빨강 / ▼ 파랑), 평가손익을 보여줍니다. 종목마다 소유자 본인의 AI 키(Anthropic,
OpenAI)로 투자 가이드를 받을 수 있고(종목별 하루 2회), 결과는 요청 당시 스냅샷과 함께 기록됩니다.

> 개발 진행 중입니다. 기능 범위와 진행 상황은 [`specs/001-stock-portfolio-ai-guide/`](specs/001-stock-portfolio-ai-guide/)의
> `spec.md`, `tasks.md`를 참고하세요.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / 빌드 | Java 25, Gradle 9 (Kotlin DSL, 단일 모듈) |
| 서버 | Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator) |
| 화면 | Thymeleaf 서버 렌더링 + htmx 부분 갱신 |
| AI | Spring AI 2.0 (Anthropic, OpenAI). 요청마다 사용자 키로 `ChatModel` 생성 |
| 시세 | 토스증권 Open API (스케줄러가 미리 받아 DB에 저장) |
| DB | PostgreSQL 18, Flyway 마이그레이션 |
| 테스트 | JUnit, ArchUnit, Testcontainers, WireMock, Playwright + axe-core, JaCoCo, k6 |

## 사전 준비

- JDK 25
- Docker Engine 27+, Docker Compose v2 (로컬 DB, 통합·E2E 테스트에 필요)
- 토스증권 Open API client id/secret: WTS 설정 > Open API에서 발급하고 서버 공인 IP를 허용 목록에 등록
- Anthropic 또는 OpenAI API 키 1개 이상 (앱 화면에서 등록)

```bash
cp .env.example .env
# .env 값 채우기
#   APP_CRYPTO_KEY=$(openssl rand -base64 32)   # AI 키 암호화용 AES-256 마스터 키
#   TOSS_CLIENT_ID=...  TOSS_CLIENT_SECRET=...
#   POSTGRES_PASSWORD=...
```

`.env`는 커밋하지 않습니다(`.gitignore` 처리, CI에서 gitleaks로 검사).

## 실행

```bash
# 로컬 개발: Spring Boot Docker Compose 지원이 db 컨테이너를 자동으로 띄웁니다
./gradlew bootRun

# 전체 스택(app + db)을 컨테이너로 실행
docker compose --profile app up -d --build
```

접속: <http://localhost:8080> — 처음 접속하면 `/setup`에서 소유자 비밀번호(12자 이상)를 설정합니다.

```bash
docker compose down        # 데이터 유지
docker compose down -v     # DB 볼륨까지 삭제
```

## 테스트와 품질 게이트

```bash
./gradlew spotlessApply                     # 포맷 자동 수정 (Palantir Java Format)
./gradlew test                              # 단위 테스트 + ArchUnit 아키텍처 규칙
./gradlew integrationTest                   # Testcontainers(PostgreSQL) + WireMock
./gradlew e2eTest                           # Playwright + axe 접근성 검사
./gradlew check                             # 포맷·린트·전체 테스트·커버리지 일괄 실행 (CI와 동일)
./gradlew dependencyCheckAnalyze            # 의존성 취약점 검사, CVSS 7 이상이면 실패
```

`check`가 통과하려면 다음을 모두 만족해야 합니다.

- Spotless, Checkstyle, Error Prone, `javac -Werror` 경고 0건
- 메서드 순환 복잡도 10 이하, 길이 50줄 이하
- 라인 커버리지 전체 80% 이상, `*.domain` 패키지 라인 90%·분기 85% 이상

## 프로젝트 구조

```text
src/main/java/com/stockmanagement/
├── common/      # Money·Currency 등 공용 도메인, 암호화, 보안·시계 설정, 웹 공통
├── auth/        # 최초 설정, 로그인, 로그인 잠금
├── portfolio/   # 계좌, 보유 종목, 대시보드
└── market/      # 종목 마스터, 종목 검색, 토스증권 시세 어댑터
src/main/resources/
├── db/migration/   # Flyway SQL
├── templates/      # Thymeleaf 화면과 공용 프래그먼트
└── static/css/     # 디자인 토큰(tokens.css), 앱 스타일
src/test/            # 단위 테스트, ArchitectureTest
src/integrationTest/ # 통합 테스트
src/e2eTest/         # E2E 테스트
specs/               # Spec Kit 기능 명세, 계획, 작업 목록
.specify/memory/constitution.md  # 프로젝트 헌법(개발 원칙)
```

각 기능 패키지는 `web → application → domain` 계층으로 나뉘고, 외부 연동과 영속성은
`application/port` 인터페이스를 구현합니다. 이 의존 방향은 `ArchitectureTest`가 강제합니다.

## 개발 원칙

개발 규칙은 [프로젝트 헌법](.specify/memory/constitution.md)을 따릅니다. 요약하면 다음과 같습니다.

- 테스트를 먼저 작성합니다(TDD).
- 금액·수량에 `double`/`float`를 쓰지 않고 `BigDecimal`/`Money`를 씁니다.
- 화면 문자열은 `messages.properties`, 스타일은 디자인 토큰과 공용 프래그먼트로 관리합니다.
- 기능 작업은 Spec Kit 흐름(`specify → clarify → plan → tasks → implement`)으로 진행합니다.
- 커밋 메시지는 Conventional Commits(`feat:`, `fix:`, `test:` 등) 형식을 씁니다.
