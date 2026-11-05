# Quickstart: 계좌별 주식 보유 관리와 AI 투자 가이드

**Feature**: `001-stock-portfolio-ai-guide`

이 문서는 기능이 처음부터 끝까지 동작함을 확인하는 실행·검증 가이드다. 구현 세부는 `tasks.md`를
본다. 화면·경로 계약은 [contracts/web-routes.md](./contracts/web-routes.md), 데이터 규칙은
[data-model.md](./data-model.md)를 본다.

## 1. 사전 준비

| 항목 | 버전/조건 |
|---|---|
| JDK | 25 (`java -version`) |
| Docker / Docker Compose | Docker Engine 27+, Compose v2 |
| 토스증권 Open API | client id·secret (시세 연동용, 운영 설정). WTS 설정 > Open API에서 발급하고 서버 공인 IP를 허용 목록에 등록 |
| AI API 키 | Anthropic 또는 OpenAI 중 최소 1개 (앱 화면에서 등록) |

```bash
cp .env.example .env
# .env 에 값 채우기
#   APP_CRYPTO_KEY=$(openssl rand -base64 32)
#   TOSS_CLIENT_ID=...   TOSS_CLIENT_SECRET=...
#   POSTGRES_PASSWORD=...
```

## 2. 실행

```bash
# 전체 스택(app + db) 컨테이너 실행
docker compose up -d --build
docker compose ps            # app, db 모두 healthy

# 또는 로컬 개발: db는 Spring Boot Docker Compose 지원이 자동 기동
./gradlew bootRun
```

접속: <http://localhost:8080>

## 3. 품질 게이트 (헌법 Quality Gates)

```bash
./gradlew spotlessCheck checkstyleMain checkstyleTest   # 원칙 I: 경고 0
./gradlew test                                          # 단위 + ArchUnit
./gradlew integrationTest                               # Testcontainers + WireMock
./gradlew e2eTest                                       # Playwright + axe (앱 자동 기동)
./gradlew jacocoTestCoverageVerification                # 전체 80%, domain 90%/85%
./gradlew dependencyCheckAnalyze                        # CVSS ≥ 7 이면 실패
docker compose --profile perf run --rm k6               # 원칙 IV 지연 예산
./gradlew check                                         # 위 Gradle 작업 일괄 실행
```

기대 결과: 모든 명령이 0으로 종료된다. k6 요약에서 `http_req_duration{type:read}` p95가 200ms 이하,
목록은 300ms 이하, 쓰기는 500ms 이하다.

## 4. 수동 검증 시나리오

각 시나리오는 spec.md의 사용자 스토리와 연결된다. E2E 테스트가 같은 시나리오를 자동화한다.

### S1. 최초 설정과 로그인 (FR-001, FR-001a)

1. 처음 접속하면 `/setup`으로 이동한다. 12자 이상 비밀번호를 설정하면 `/login`으로 간다.
2. 틀린 비밀번호를 5번 입력하면 "15분 후 다시 시도" 안내가 나오고, 맞는 비밀번호도 거부된다.
3. `/setup`에 다시 접속하면 404가 나온다.

### S2. 계좌·보유 종목 등록 (US1)

1. 계좌 "키움 일반"을 만들고, `삼성전자`를 검색해 평단가 `71500`, 수량 `10`으로 등록한다.
   → `₩71,500 · 10주`로 표시된다.
2. 계좌 "미래에셋 해외"를 만들고, `AAPL`을 평단가 `185.20`, 수량 `0.35`로 등록한다.
   → `$185.2000 · 0.35주`로 표시된다.
3. 같은 계좌에 삼성전자를 다시 등록하면 중복 오류가 나고 저장되지 않는다.
4. 평단가 `0`을 입력하면 입력란 옆에 오류가 나온다.
5. 계좌를 삭제하면 확인 대화상자가 나오고, 확인하면 계좌가 목록에서 사라진다.

### S3. 현재가·색상 (US2)

1. 장중에 대시보드를 연다. 각 종목 옆 현재가, `▲/▼` 기호, 빨강/파랑 색이 전일 종가 대비 등락과
   맞는다.
2. 평가손익과 수익률 색이 손익 부호와 맞는다.
3. 1분 정도 기다리면 "HH:mm:ss 기준" 시각이 갱신된다.
4. (실패 재현) `TOSS_CLIENT_SECRET`을 잘못된 값으로 바꾸고 재시작한다. 1분 후 종목이 "지연 (HH:mm 기준)"으로
   표시되고 마지막 값은 유지된다.
5. 장 마감 후에는 "장 마감"과 종가가 표시된다.

### S4. AI 키 등록과 선택 (US4)

1. 가이드 버튼을 누르면 키가 없으므로 `/settings/ai`로 이동한다.
2. 잘못된 키를 입력하면 "키를 확인할 수 없습니다"가 나오고 입력란은 비워진다.
3. 올바른 Anthropic 키를 입력하면 `****abcd`로 표시되고 자동 선택된다.
4. OpenAI 키도 등록하고 OpenAI를 선택한다.
5. 키 원문이 로그에 없는지 확인한다: `docker compose logs app | grep -c '<키 일부>'` → `0`

### S5. 가이드 요청과 한도 (US3)

1. 삼성전자의 가이드를 누르면 진행 표시가 나오고, 30초 안에 요약·진단·행동·근거·위험·면책 문구와
   "OpenAI · <모델명>"이 표시된다. 남은 횟수는 1이다.
2. 다시 요청하면 남은 횟수가 0이 되고 버튼이 비활성화되며 "내일 00:00 (KST) 초기화"가 표시된다.
3. AAPL 가이드 버튼은 여전히 "남은 2회"다.
4. (실패 재현) 등록된 키를 무효화하고 요청하면 "AI 키를 확인해 주세요"가 나오고 횟수는 그대로다.
5. (동시성) 두 탭에서 같은 종목 가이드를 동시에 여러 번 누른다. 생성된 가이드는 2건을 넘지 않는다.
   자동 검증은 `GuideQuotaConcurrencyIT`가 맡는다.

### S6. 가이드 기록 (US5)

1. 종목의 "기록" 화면에서 가이드가 최신순으로 나오고, 각 기록에 요청 당시 현재가·평단가·수량·
   수익률이 보인다.
2. 평단가를 수정한 뒤 기록을 다시 봐도 당시 값이 그대로다.
3. 보유 종목을 삭제해도 기록은 남고 "보유 종료"로 표시된다.

## 5. 정리

```bash
docker compose down          # 데이터 유지
docker compose down -v       # DB 볼륨까지 삭제
```
