# Stock Management

카카오 간편로그인으로 여러 사용자가 각자 쓰는 주식 포트폴리오 웹 앱입니다. 계좌별로 한국·미국 보유 종목(평단가,
수량)을 등록하면 현재가, 전일 대비 등락(▲ 빨강 / ▼ 파랑), 평가손익을 보여줍니다. 종목마다 관리자가 등록한 공용 AI 키(Anthropic,
OpenAI)로 투자 가이드를 받을 수 있고(사용자·종목별 하루 2회), 결과는 요청 당시 스냅샷과 함께 기록됩니다.

주요 한도:
- 사용자별로 계좌 최대 2개, 보유 종목 최대 20개(데이터는 사용자마다 분리, 종목·시세는 공유)
- AI 가이드는 종목별 하루 2회(KST 자정 초기화, 실패 시 차감 없음)
- 가이드는 기술적 흐름·수급(거래량, 국내 종목은 투자자별 매매·공매도)·국내외 이슈(Claude 웹 검색, 출처 링크)를 종합한다

기능 명세와 진행 상황은 [`specs/001-stock-portfolio-ai-guide/`](specs/001-stock-portfolio-ai-guide/)를, 실행·검증
시나리오는 [quickstart.md](specs/001-stock-portfolio-ai-guide/quickstart.md)를 참고하세요.

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
- 토스증권 Open API client id/secret: WTS 설정 > Open API에서 발급하고 **서버 공인 IP를 허용 목록에 등록**(등록하지 않으면 시세 호출이 거부된다)
- Anthropic 또는 OpenAI API 키 1개 이상 (앱 화면에서 등록)

```bash
cp .env.example .env
# .env 값 채우기
#   APP_CRYPTO_KEY=$(openssl rand -base64 32)   # AI 키 암호화용 AES-256 마스터 키
#   TOSS_CLIENT_ID=...  TOSS_CLIENT_SECRET=...
#   POSTGRES_PASSWORD=...
```

`.env`는 커밋하지 않습니다(`.gitignore` 처리, CI에서 gitleaks로 검사).

세션 쿠키에는 `Secure` 속성이 붙습니다. HTTPS 없이(예: 사설망에서 `http://서버IP:8080`) 운영하면 `.env`에
`SESSION_COOKIE_SECURE=false`를 넣어야 로그인이 유지됩니다. `localhost`는 브라우저가 허용하므로 필요 없습니다.

## 실행

```bash
# 로컬 개발: Spring Boot Docker Compose 지원이 db 컨테이너를 자동으로 띄웁니다
./gradlew bootRun

# 전체 스택(app + db)을 컨테이너로 실행
docker compose --profile app up -d --build
```

접속: <http://localhost:8080> — "카카오로 시작하기"로 로그인합니다(처음이면 자동 가입).

카카오 로그인 준비([developers.kakao.com](https://developers.kakao.com)):
- 앱을 만들고 카카오 로그인을 켭니다. Redirect URI에 `http://localhost:8080/login/oauth2/code/kakao`(운영은 실제 도메인)를 등록합니다.
- 동의 항목은 "닉네임"(선택 동의)만 씁니다.
- `.env`에 `KAKAO_CLIENT_ID`(REST API 키), `KAKAO_CLIENT_SECRET`(켠 경우), `KAKAO_ADMIN_KEY`(탈퇴 시 연결 해제용)를 넣습니다.
- 관리자 카카오 회원번호를 `ADMIN_KAKAO_IDS`(쉼표 구분)에 넣습니다. 관리자만 AI 설정 메뉴가 보이고, 관리자가 등록한
  AI 키를 모든 사용자가 씁니다(사용 요금은 그 키의 공급자 계정에 청구). 회원번호는 한 번 로그인한 뒤
  `app_user.kakao_id`에서 확인하고, 바꾼 뒤에는 다시 로그인해야 반영됩니다.

기존(비밀번호 로그인 시절) 데이터가 있는 DB에 배포하면, **배포 후 처음 카카오로 로그인한 사용자**가 기존 데이터를
넘겨받습니다. 공개하기 전에 운영자가 먼저 로그인하세요(`specs/002-kakao-login-multiuser/quickstart.md` 2장).

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

성능 예산(헌법 원칙 IV) 측정: 앱을 띄우고 기준 데이터셋을 넣은 뒤 k6를 실행합니다.

```bash
docker compose exec -T db psql -U stock -d stock -v ON_ERROR_STOP=1 < perf/seed.sql   # 계좌 2·보유 20·가이드 기록 1만 건
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 -v "$PWD/perf:/scripts" grafana/k6 run /scripts/budget.js
```

p95 예산은 단건 200ms, 목록·검색 300ms, 쓰기 500ms이며 `perf/baseline.json` 대비 20% 넘게 느려지면 실패합니다.
프런트엔드 예산(LCP·CLS·INP·초기 JS 용량·100ms 피드백)은 `e2eTest`의 `WebVitalsE2E`가 검사합니다.

`check`가 통과하려면 다음을 모두 만족해야 합니다.

- Spotless, Checkstyle, Error Prone, `javac -Werror` 경고 0건
- 메서드 순환 복잡도 10 이하, 길이 50줄 이하
- 라인 커버리지 전체 80% 이상, `*.domain` 패키지 라인 90%·분기 85% 이상

## 배포

AWS Lightsail(서울)에 Terraform으로 인프라를 만들고, main에 머지되면 GitHub Actions가 CI 통과 후 자동 배포합니다.
컨테이너 서비스 + 관리형 PostgreSQL + Route 53 도메인 구성이며, 처음 설정 순서는 [`infra/README.md`](infra/README.md)에
있습니다.

## 프로젝트 구조

```text
src/main/java/com/stockmanagement/
├── common/      # Money·Currency 등 공용 도메인, 암호화, 보안·시계 설정, 웹 공통
├── auth/        # 최초 설정, 로그인, 로그인 잠금
├── portfolio/     # 계좌, 보유 종목, 대시보드
├── market/        # 종목 마스터, 종목 검색, 시세·수급, 토스증권 어댑터
├── aicredential/  # AI 공급자 키 등록·암호화 저장·선택
└── guide/         # AI 가이드 요청·한도·실행(Spring AI)·기록
src/main/resources/
├── db/migration/   # Flyway SQL
├── templates/      # Thymeleaf 화면과 공용 프래그먼트
└── static/css/     # 디자인 토큰(tokens.css), 앱 스타일
src/test/            # 단위 테스트, ArchitectureTest
src/integrationTest/ # 통합 테스트
src/e2eTest/         # E2E 테스트
perf/                # k6 성능 예산 스크립트, 기준 데이터셋 시드, 기준선
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
