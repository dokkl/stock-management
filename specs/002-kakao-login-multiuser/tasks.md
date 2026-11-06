---

description: "Task list for 002-kakao-login-multiuser"
---

# Tasks: 카카오 간편로그인과 다중 사용자 지원

**Input**: Design documents from `/specs/002-kakao-login-multiuser/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 헌법 원칙 II(Test-First, NON-NEGOTIABLE)에 따라 **테스트 작업을 포함한다**. 각 스토리의 테스트를 먼저 작성해
실패(Red)를 확인한 뒤 구현한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1~US4)
- Include exact file paths in descriptions

## Path Conventions

단일 Gradle 모듈(기능 001과 동일). 약어:

- `MAIN` = `src/main/java/com/stockmanagement`
- `RES` = `src/main/resources`
- `UT` = `src/test/java/com/stockmanagement`
- `IT` = `src/integrationTest/java/com/stockmanagement`
- `E2E` = `src/e2eTest/java/com/stockmanagement`

Flyway 버전: V9(app_user·소유권, Foundational), V10(kakao_unlink_queue, US4).

**순서에 관한 주의**: V9가 `account`·`ai_credential`·`guide_request`의 `user_id`를 NOT NULL로 만들기 때문에, 모든 저장소에
사용자 범위를 넣는 일(T012~T016)은 어느 스토리보다 먼저 끝나야 빌드가 통과한다. 그래서 Foundational에 둔다. US2는 그
격리가 모든 경로에서 지켜지는지 교차 사용자 테스트로 고정하고 빈틈을 메운다.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: OAuth2 의존성, 카카오 설정, 테스트용 카카오 대역

- [X] T001 `build.gradle.kts`의 dependencies에 `implementation("org.springframework.boot:spring-boot-starter-oauth2-client")`를 추가하고, `integrationTest`·`e2eTest` 스위트에 `org.springframework.boot:spring-boot-starter-security-test`가 있는지 확인한다(없으면 e2eTest에도 추가)
- [X] T002 [P] `RES/application.yml`에 카카오 OAuth2 설정을 추가한다(research R1, contracts/kakao-oauth.md): `spring.security.oauth2.client.registration.kakao`(client-id `${KAKAO_CLIENT_ID:}`, client-secret `${KAKAO_CLIENT_SECRET:}`, client-authentication-method `client_secret_post`, authorization-grant-type `authorization_code`, redirect-uri `{baseUrl}/login/oauth2/code/{registrationId}`, scope `profile_nickname`, client-name `카카오`), `provider.kakao`(authorization-uri `${app.kakao.auth-base-url}/oauth/authorize`, token-uri `${app.kakao.auth-base-url}/oauth/token`, user-info-uri `${app.kakao.api-base-url}/v2/user/me`, user-name-attribute `id`), `app.kakao.auth-base-url: https://kauth.kakao.com`, `app.kakao.api-base-url: https://kapi.kakao.com`, `app.kakao.admin-key: ${KAKAO_ADMIN_KEY:}`. (이 파일에는 커밋하지 않은 외부 변경 `.env` import가 있으므로 커밋 시 자기 변경만 스테이징한다)
- [X] T003 [P] `.env.example`에 `KAKAO_CLIENT_ID=`, `KAKAO_CLIENT_SECRET=`, `KAKAO_ADMIN_KEY=`를 설명 주석과 함께 추가한다
- [X] T004 [P] 카카오 WireMock 대역 `IT/support/KakaoStubs.java`와 `E2E/support/KakaoStubs.java`를 만든다(contracts/kakao-oauth.md "테스트 대역"):
  - `user(long kakaoId, String nickname)`: `POST /oauth/token`(본문 `code=test-code-{kakaoId}`) → `access_token=token-{kakaoId}`, `GET /v2/user/me`(헤더 `Authorization: Bearer token-{kakaoId}`) → `{"id":kakaoId,"kakao_account":{"profile":{"nickname":…}}}`(nickname null이면 profile 생략)
  - `authorizeAs(long kakaoId)`: `GET /oauth/authorize` → 302 `{redirect_uri}?code=test-code-{kakaoId}&state={{request.query.state}}`(응답 템플릿)
  - `cancelAuthorize()`: 302 `{redirect_uri}?error=access_denied&state=…`
  - `unlinkOk()` / `unlinkFails(int status)`: `POST /v1/user/unlink`
  - `IntegrationTestBase`·`E2ETestBase`의 `WIREMOCK`에 응답 템플릿을 켜고(`wireMockConfig().globalTemplating(true)` 또는 해당 확장), `app.kakao.auth-base-url`·`app.kakao.api-base-url`을 `WIREMOCK.baseUrl()`로, `KAKAO_CLIENT_ID`=`test-client-id`, `app.kakao.admin-key`=`test-admin-key`로 등록한다

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 사용자 도메인·스키마, 현재 사용자 해석, 모든 저장소의 사용자 범위. 이 단계가 끝나야 어떤 스토리도 동작한다.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests

- [X] T005 [P] 사용자 도메인 단위 테스트를 작성한다: `UT/user/domain/DisplayNameTest.java`(null·공백 → "카카오 사용자", 앞뒤 공백 제거, 40자 초과는 40자로 자름), `UT/user/domain/KakaoIdTest.java`(양수만 허용, 0·음수 거부), `UT/user/domain/UserIdTest.java`(양수, equals)
- [X] T006 [P] 마이그레이션 통합 테스트를 `IT/user/LegacyMigrationIT.java`에 작성한다. 별도 스키마에서 Flyway를 V8까지 적용한 뒤 owner 행·계좌·AI 키·가이드 기록을 넣고 V9를 적용하면: `app_user`에 `legacy = true`, `kakao_id IS NULL`, owner의 `selected_ai_provider`를 승계한 행 1개가 생기고 세 테이블의 `user_id`가 그 행을 가리키며 `owner` 테이블이 없다. owner 행이 없으면 레거시 사용자도 없다

### Implementation

- [X] T007 Flyway `RES/db/migration/V9__app_user.sql`을 data-model.md "마이그레이션 순서"대로 작성한다(한 트랜잭션):
  - `app_user`: id BIGINT identity PK, kakao_id BIGINT NULL, display_name VARCHAR(40) NOT NULL, legacy BOOLEAN NOT NULL DEFAULT false, selected_ai_provider VARCHAR(20) NULL `CHECK (selected_ai_provider IN ('ANTHROPIC','OPENAI'))`, created_at TIMESTAMPTZ NOT NULL, last_login_at TIMESTAMPTZ NULL, `CHECK (NOT legacy OR kakao_id IS NULL)`
  - `CREATE UNIQUE INDEX ux_app_user_kakao ON app_user (kakao_id) WHERE kakao_id IS NOT NULL`, `CREATE UNIQUE INDEX ux_app_user_legacy ON app_user (legacy) WHERE legacy`
  - owner 행이 있으면 레거시 사용자 INSERT(display_name '기존 사용자')
  - `account`·`ai_credential`·`guide_request`에 user_id BIGINT 추가 → 레거시 사용자로 채움 → NOT NULL, FK → app_user `ON DELETE CASCADE`
  - `ux_account_name` 삭제 후 `(user_id, name) WHERE deleted_at IS NULL` 유니크, `ix_account_user (user_id) WHERE deleted_at IS NULL`
  - `ai_credential`의 provider UNIQUE 제약 삭제 후 `(user_id, provider)` UNIQUE
  - `ux_guide_slot` 삭제 후 `(user_id, security_id, usage_date, slot) WHERE status <> 'FAILED'`, `ix_guide_history` 삭제 후 `(user_id, security_id, requested_at DESC) WHERE status = 'SUCCEEDED'`
  - `owner` 테이블 삭제
  - (T006 통과)
- [X] T008 [P] 사용자 도메인을 `MAIN/user/domain/`에 구현한다: `UserId`(record, 양수), `KakaoId`(record, 양수), `DisplayName`(record, `of(String)` 정규화, 기본값 "카카오 사용자", 최대 40자), `AppUser`(id, kakaoId, displayName, selectedAiProvider). (T005 통과)
- [X] T009 `UserStore` 포트(`MAIN/user/application/port/UserStore.java`)와 `JdbcUserStore`(`MAIN/user/persistence/JdbcUserStore.java`)를 구현한다: `findByKakaoId`, `claimLegacy(KakaoId, DisplayName, Instant)`(UPDATE … WHERE legacy AND kakao_id IS NULL RETURNING id), `insert(KakaoId, DisplayName, Instant)`, `touchLogin(UserId, DisplayName, Instant)`, `lock(UserId)`(SELECT … FOR UPDATE), `delete(UserId)`, `find(UserId)`
- [X] T010 [P] 현재 사용자 해석을 `MAIN/user/web/`에 만든다: `AppUserPrincipal`(OAuth2User 구현, 앱 `UserId`·표시 이름 보유, `getName()`은 UserId 문자열), `CurrentUser` 애노테이션 + `CurrentUserArgumentResolver`(`@CurrentUser UserId userId` 파라미터 주입, `WebMvcConfigurer` 등록). 애플리케이션·도메인 계층은 Spring Security에 의존하지 않는다
- [X] T011 [P] ArchUnit 규칙을 `UT/ArchitectureTest.java`에 추가한다: `..application..`과 `..domain..`은 `org.springframework.security..`에 의존하지 않는다
- [X] T012 포트폴리오에 사용자 범위를 넣는다: `PortfolioStore`·`DashboardQuery` 모든 메서드에 `UserId` 인자를 추가하고 `JdbcPortfolioStore`·`DashboardJdbcQuery`(`MAIN/portfolio/persistence/`)의 SQL에 `account.user_id = :userId` 조건(holding은 account JOIN)과 INSERT 시 user_id를 넣는다. `AccountService`·`HoldingService`(`MAIN/portfolio/application/`)와 `AccountController`·`HoldingController`·`DashboardController`·`QuoteFragmentController`(`MAIN/portfolio/web/`)가 `@CurrentUser UserId`를 받아 넘긴다. `countActiveAccounts(UserId)`, `countHoldings(UserId)`로 바꾼다
- [X] T013 AI 키에 사용자 범위를 넣는다: `CredentialStore`(`MAIN/aicredential/application/port/`) 모든 메서드에 `UserId`, `JdbcCredentialStore`는 `ai_credential.user_id` 조건과 `app_user.selected_ai_provider`(owner 대신) 사용, `CredentialService`·`AiSettingsController`가 `UserId`를 받는다. `keyFor(UserId, AiProvider)`, `selectedProvider(UserId)`
- [X] T014 가이드에 사용자 범위를 넣는다: `GuideStore`·`GuideHistoryQuery`(`MAIN/guide/application/port/`)에 `UserId`, `JdbcGuideStore`·`GuideHistoryJdbcQuery`(`MAIN/guide/persistence/`)는 `guide_request.user_id` 조건, 예약 INSERT에 user_id, `ON CONFLICT (user_id, security_id, usage_date, slot) WHERE status <> 'FAILED'`, `countActive(UserId, securityId, date)`. `GuideJob`에 `UserId`를 담아 `GuideRunner`가 그 사용자의 키(`keyFor(userId, provider)`)를 쓴다. `GuideService`·`GuideController`·`GuideHistoryController`가 `UserId`를 받는다. `DashboardJdbcQuery`의 오늘 사용 수 집계에 `user_id = :userId`를 추가한다
- [X] T015 통합 테스트 지원을 바꾼다: `IT/support/IntegrationTestBase.java`의 `owner()`를 `user(UserId)`(Spring Security Test `oauth2Login().oauth2User(new AppUserPrincipal(...))`)와 `defaultUser()`로 대체하고, `UT/support/Fixtures.java`에 `user(long kakaoId)`(app_user INSERT, 반환 UserId)와 `account(UserId, String)` 오버로드를 추가한다. `IT/support/GuideFixture.java`도 사용자 인자를 받게 한다. 기존 IT(`IT/portfolio/*`, `IT/aicredential/*`, `IT/guide/*`, `IT/market/QuoteFragmentIT.java`, `IT/auth/SecurityHeadersIT.java`)를 새 헬퍼로 옮긴다
- [X] T016 E2E 지원을 바꾼다: `E2E/support/E2ETestBase.java`의 `setupAndLogin()`을 `loginAs(long kakaoId, String nickname)`(KakaoStubs로 user·authorizeAs 등록 → `/login` → "카카오로 시작하기" 클릭 → `/` 대기)으로 대체하고, 기존 E2E(`E2E/aicredential`, `E2E/guide`, `E2E/market`, `E2E/perf`, `E2E/portfolio`)를 옮긴다. 시드는 `Fixtures.user(...)`로 만든 사용자에 연결한다

**Checkpoint**: 스키마·사용자 범위가 들어가고 기존 기능 테스트가 새 로그인 헬퍼로 통과한다(카카오 실제 로그인 흐름은 US1).

---

## Phase 3: User Story 1 - 카카오 계정으로 가입·로그인 (Priority: P1) 🎯 MVP

**Goal**: "카카오로 시작하기" 하나로 가입·로그인하고, 비밀번호 로그인·최초 설정·잠금을 없앤다.

**Independent Test**: 처음 보는 카카오 계정으로 로그인하면 빈 대시보드, 로그아웃 후 같은 계정으로 다시 로그인하면 같은 사용자(quickstart K1).

### Tests for User Story 1 ⚠️

- [X] T017 [P] [US1] 카카오 로그인 흐름 통합 테스트를 `IT/user/KakaoLoginIT.java`에 작성한다(MockMvc + KakaoStubs): `GET /oauth2/authorization/kakao` → 302(state 포함) → 같은 세션으로 `GET /login/oauth2/code/kakao?code=test-code-1001&state=…` → 302 `/`, `app_user` 1행(kakao_id 1001, display_name 닉네임). 같은 계정 재로그인은 행이 늘지 않고 `last_login_at` 갱신. `error=access_denied` → 302 `/login?error=cancelled`. state 불일치 → `/login?error=failed`. user/me에 id 없음 → failed. 닉네임 없음 → "카카오 사용자". `/setup`은 404, `POST /login`은 비밀번호 로그인이 없다(405 또는 403)
- [X] T018 [P] [US1] 레거시 인수 통합 테스트를 `IT/user/LegacyClaimIT.java`에 작성한다: 레거시 사용자와 그 계좌가 있을 때 첫 `UserService.signIn(kakao 2001)`이 레거시 행을 차지해 계좌가 보이고, 두 번째 새 카카오 사용자는 빈 상태다. 서로 다른 카카오 ID 2개가 가상 스레드로 동시에 첫 로그인하면 정확히 1명만 인수한다(50회 반복)
- [X] T019 [P] [US1] 세션 통합 테스트 `IT/auth/SessionTimeoutIT.java`를 갱신한다: 7일 비활성 만료와 HttpOnly·Secure·SameSite=Lax 유지, 로그인 성공 시 세션 ID가 바뀐다(세션 고정 방지)
- [X] T020 [P] [US1] E2E를 `E2E/user/KakaoLoginE2E.java`에 작성한다(quickstart K1): 로그인 화면에 비밀번호 입력란 없음·카카오 버튼만, 첫 로그인 → 빈 대시보드와 상단 닉네임, 로그아웃 → `/login?logout`, 재로그인 → 같은 데이터, 취소 → "카카오 로그인이 취소되었습니다", 로그인 화면 axe 위반 0건(카카오 버튼 대비 포함)

### Implementation for User Story 1

- [X] T021 [US1] `UserService.signIn(KakaoId, DisplayName)`을 `MAIN/user/application/UserService.java`에 구현한다(research R2·R6): 기존 사용자면 `touchLogin`, 없으면 `claimLegacy` 시도 → 1행이면 `INFO` 감사 로그 `레거시 데이터 인수: kakaoId=…` → 0행이면 `insert`. 한 트랜잭션. (T018 통과)
- [X] T022 [US1] `KakaoOAuth2UserService`를 `MAIN/user/adapter/kakao/KakaoOAuth2UserService.java`에 구현한다: 기본 `DefaultOAuth2UserService`로 사용자 정보를 받고 `id`(숫자 필수)·`kakao_account.profile.nickname`(선택)을 검증해 `UserService.signIn` 결과로 `AppUserPrincipal`을 돌려준다. 실패는 `OAuth2AuthenticationException`. 토큰·응답 본문을 로그에 남기지 않는다
- [X] T023 [US1] `SecurityConfig`(`MAIN/common/config/SecurityConfig.java`)를 바꾼다: `formLogin` 제거, `oauth2Login(loginPage "/login", userInfoEndpoint → KakaoOAuth2UserService, defaultSuccessUrl "/" true, failureHandler: `access_denied` → `/login?error=cancelled`, 그 외 → `/login?error=failed`)`, permitAll에 `/login`, `/oauth2/**`, `/login/oauth2/**` 추가, `/setup` 제거, `SetupRequiredFilter`·`PasswordEncoder` 빈 제거. `HtmxAwareAuthenticationEntryPoint`는 `MAIN/user/web/`로 옮긴다. (T017 통과)
- [X] T024 [US1] 로그인 화면을 바꾼다: `MAIN/user/web/LoginController.java`(`GET /login`, error·logout·withdrawn 안내 모델), `RES/templates/auth/login.html`(카카오 공식 버튼 형태 "카카오로 시작하기" 링크 `/oauth2/authorization/kakao`, 비밀번호 폼 제거), `RES/static/css/tokens.css`에 `--color-kakao: #FEE500`, `--color-kakao-text: #191919` 토큰, `RES/static/css/app.css`에 `.btn-kakao`, `RES/messages.properties`에 login.kakao·login.error.cancelled("카카오 로그인이 취소되었습니다")·login.error.failed("카카오 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요")·login.logout·login.withdrawn. `RES/templates/layout.html` 헤더에 표시 이름(현재 사용자) 추가. (T020 통과)
- [X] T025 [US1] 비밀번호 인증 코드를 제거한다: `MAIN/auth/application/{SetupService,LoginAttemptService,SetupAlreadyCompletedException,port/OwnerStore}.java`, `MAIN/auth/domain/{Owner,LoginLockPolicy,LoginDecision}.java`, `MAIN/auth/persistence/*`, `MAIN/auth/web/{SetupController,SetupRequiredFilter,OwnerAuthenticationProvider,LoginController}.java`, `RES/templates/auth/setup.html`, 관련 메시지 키, `UT/auth/domain/LoginLockPolicyTest.java`, `IT/auth/AuthFlowIT.java`, `E2E/auth/AuthE2E.java`. 비게 된 `auth` 패키지를 지운다

**Checkpoint**: 카카오로 가입·로그인·로그아웃이 되고 기존 기능이 로그인한 사용자 범위로 동작한다.

---

## Phase 4: User Story 2 - 사용자별 데이터 분리 (Priority: P1)

**Goal**: 사용자는 자기 데이터만 보고 바꾼다. 남의 항목은 404로 존재 여부도 드러나지 않는다(SC-003).

**Independent Test**: A·B가 각각 데이터를 만든 뒤 서로의 화면에 상대 데이터가 없고, 상대 항목 주소로 직접 요청하면 404(quickstart K2).

### Tests for User Story 2 ⚠️

- [X] T026 [P] [US2] 포트폴리오 교차 사용자 테스트를 `IT/portfolio/CrossUserPortfolioIT.java`에 작성한다: B가 A의 `PUT/DELETE /accounts/{id}`, `GET /accounts/{id}/edit|header`, `POST /accounts/{id}/holdings`, `GET/PUT/DELETE /holdings/{id}`, `GET /holdings/{id}/edit`에 요청하면 모두 404이고 응답 본문이 없는 id와 같으며 A의 데이터가 바뀌지 않는다. `/`·`/fragments/quotes`에는 자기 것만, 같은 계좌명 "키움 일반"은 A·B 모두 생성 가능
- [X] T027 [P] [US2] AI 키 교차 사용자 테스트를 `IT/aicredential/CrossUserCredentialIT.java`에 작성한다: A가 키를 등록해도 B의 `/settings/ai`는 "키 필요", B의 선택·삭제가 A에 영향 없음, A·B가 같은 공급자 키를 각각 등록 가능
- [X] T028 [P] [US2] 가이드 교차 사용자 테스트를 `IT/guide/CrossUserGuideIT.java`에 작성한다: B가 A의 `/guides/{id}`, `/guides/{id}/status`, `POST /holdings/{A의 id}/guides`, `/holdings/{id}/guide-button`에 요청하면 404, `/securities/{id}/guides`는 같은 종목이어도 B 자신의 기록만 보인다. B의 가이드는 A의 키로 호출되지 않는다(WireMock 요청 헤더 검증)
- [X] T029 [P] [US2] 공유 시세 테스트를 `IT/market/QuoteRefreshSchedulerIT.java`에 추가한다: A·B가 같은 종목을 보유하면 시세 조회는 한 번(중복 제거)이고 둘 다 같은 현재가를 본다
- [X] T030 [P] [US2] E2E를 `E2E/user/DataIsolationE2E.java`에 작성한다(quickstart K2): 브라우저 컨텍스트 2개로 A·B 로그인, B 화면에 A 데이터 없음, B가 A의 보유 수정 주소를 열면 404 화면, axe 위반 0건

### Implementation for User Story 2

- [X] T031 [US2] T026~T029에서 드러난 사용자 조건 누락을 고친다(대상: `MAIN/portfolio/persistence/*`, `MAIN/aicredential/persistence/*`, `MAIN/guide/persistence/*`, `MAIN/*/web/*`). 404 응답이 남의 항목과 없는 항목에서 같도록 `ResourceNotFoundException` 경로를 통일한다
- [X] T032 [US2] 시세 스케줄러의 보유 종목 조회(`MAIN/market/persistence/JdbcQuoteStore.java` `findHeldSecurities`)가 모든 사용자 보유의 합집합(중복 없음)임을 확인하고, 삭제된 계좌 제외 조건을 유지한다(T029 통과)

**Checkpoint**: 교차 사용자 접근이 모든 경로에서 404이고 데이터 노출 0건이다.

---

## Phase 5: User Story 3 - 사용자별 한도 (Priority: P2)

**Goal**: 계좌 2·보유 20·종목별 하루 가이드 2회를 사용자마다 따로 센다.

**Independent Test**: A가 한도를 다 써도 B는 영향 없음(quickstart K3).

### Tests for User Story 3 ⚠️

- [X] T033 [P] [US3] 한도 통합 테스트를 `IT/portfolio/PerUserLimitsIT.java`에 작성한다: A 계좌 2개 후 A의 세 번째는 422 "계좌는 최대 2개", B는 생성 가능. A 보유 20건 후 A는 422, B는 등록 가능. 같은 사용자의 계좌 생성 동시 요청 10건에서도 2개를 넘지 않는다
- [X] T034 [P] [US3] 가이드 한도 통합 테스트를 `IT/guide/PerUserGuideQuotaIT.java`에 작성한다: A가 삼성전자 2회 후 B는 남은 2회, A가 같은 종목을 두 계좌에 보유하면 합산 2회. 동시성: A·B 각각 20건 동시 요청 × 100회에서 사용자마다 비실패 2건(SC-005)

### Implementation for User Story 3

- [X] T035 [US3] `AccountService.create`·`HoldingService.add`(`MAIN/portfolio/application/`)가 같은 트랜잭션에서 `UserStore.lock(userId)` 후 사용자별 개수로 한도를 검사한다(research R5). (T033 통과)
- [X] T036 [US3] `GuideService`·`JdbcGuideStore`의 진행 중 요청 조회·예약·집계가 `(user_id, security_id, usage_date)` 단위임을 확인하고, 가이드 버튼·대시보드의 남은 횟수가 사용자별로 나오게 한다(`MAIN/guide/`, `MAIN/portfolio/persistence/DashboardJdbcQuery.java`). (T034 통과)

**Checkpoint**: 한 사용자의 한도 소진이 다른 사용자에게 영향을 주지 않는다.

---

## Phase 6: User Story 4 - 회원 탈퇴 (Priority: P3)

**Goal**: 확인 후 탈퇴하면 사용자 데이터를 모두 지우고 카카오 연결을 해제한다. 다시 로그인하면 새 사용자다.

**Independent Test**: 데이터가 있는 사용자가 탈퇴 → 로그아웃, 재로그인 → 빈 대시보드(quickstart K4).

### Tests for User Story 4 ⚠️

- [X] T037 [P] [US4] 탈퇴 통합 테스트를 `IT/user/WithdrawalIT.java`에 작성한다:
  - `POST /settings/account/withdraw`(CSRF) → 302 `/login?withdrawn`, 세션 무효
  - 그 사용자의 `account`·`holding`·`ai_credential`·`guide_request`·`app_user` 행 0건, 다른 사용자 데이터는 그대로(SC-006)
  - WireMock `POST /v1/user/unlink`가 `Authorization: KakaoAK test-admin-key`, 본문 `target_id_type=user_id&target_id={kakaoId}`로 1회 호출
  - unlink 503 → `kakao_unlink_queue`에 행, 재시도 스케줄러 실행 후 200이면 행 삭제, 10회 실패 후 중단
  - 진행 중 가이드가 있던 사용자가 탈퇴해도 오류 없이 결과가 버려진다
  - 같은 카카오 ID로 다시 로그인하면 새 `app_user`(이전 데이터 없음)
  - 미인증 401, CSRF 없음 403
- [X] T038 [P] [US4] E2E를 `E2E/user/WithdrawalE2E.java`에 작성한다(quickstart K4): `/settings/account` → "탈퇴" → 확인 대화상자 → `/login?withdrawn` 안내, 재로그인 → 빈 대시보드, axe 위반 0건

### Implementation for User Story 4

- [X] T039 [US4] Flyway `RES/db/migration/V10__kakao_unlink_queue.sql`: `kakao_unlink_queue`(kakao_id BIGINT PK, attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL, last_error VARCHAR(100) NULL)
- [X] T040 [P] [US4] `KakaoUnlinker` 포트(`MAIN/user/application/port/KakaoUnlinker.java`, 결과 `UNLINKED | FAILED(status)`)와 `KakaoUnlinkClient`(`MAIN/user/adapter/kakao/KakaoUnlinkClient.java`, RestClient, 헤더 `KakaoAK {admin-key}`, 타임아웃 5초, "앱에 연결되지 않은 사용자" 응답은 UNLINKED, 키·본문 로그 금지)
- [X] T041 [US4] `WithdrawalService`(`MAIN/user/application/WithdrawalService.java`): 트랜잭션에서 `UserStore.delete`(연쇄 삭제) → 커밋 후 `KakaoUnlinker` 호출 → 실패 시 큐 등록. `UnlinkRetryScheduler`(`MAIN/user/application/UnlinkRetryScheduler.java`, `@Scheduled` 1분, 지수 백오프 1분·2분·4분… 상한 6시간, 최대 10회)
- [X] T042 [US4] 회원 정보 화면: `MAIN/user/web/AccountSettingsController.java`(`GET /settings/account`, `POST /settings/account/withdraw` → 세션 무효화·SecurityContext 정리·`/login?withdrawn`, htmx면 `HX-Redirect`), `RES/templates/settings/account.html`(표시 이름, 가입일, 탈퇴 버튼 `hx-confirm` 공용 대화상자, 탈퇴 영향 설명), `RES/templates/layout.html` 헤더 표시 이름을 이 화면 링크로, `RES/messages.properties` 키 추가. (T037·T038 통과)

**Checkpoint**: 모든 사용자 스토리가 독립적으로 동작한다.

---

## Phase 7: Polish & Cross-Cutting Concerns

**진행 메모 (2026-11-06)**
- T043: 사용자 50명이 카카오 대역(WireMock, `X-Perf-User` 헤더로 사용자 지정)으로 OAuth 로그인하는 k6 50 VU·60초를 실제로 돌려 헌법 예산 안(p95 목록 235ms, 단건 75ms, 쓰기 103ms, 체크 100%)임을 확인하고 `perf/baseline.json`을 이 시나리오로 다시 기록했다(이전 5 VU 기준선과 부하가 달라 비교 불가).
- T048: 실제 카카오 개발자 앱(앱 키·Redirect URI)이 필요해 남아 있다.
- 구현 중 발견: V9에 `holding.account_id` FK의 `ON DELETE CASCADE`가 빠져 탈퇴가 실패해 추가했다. 지원하지 않는 HTTP 메서드가 500이던 공통 예외 처리기를 405로 고쳤다. 세션 고정 방지는 MockMvc로 판별이 어려워 E2E(실제 쿠키)로 검증한다.

- [X] T043 [P] 성능 시드·스크립트를 다중 사용자로 바꾼다: `perf/seed.sql`에 app_user 50명(1명은 기준 데이터셋 계좌 2·보유 20·가이드 기록 1만, 49명은 계좌 1·보유 5), `compose.yaml` perf 프로필에 WireMock 카카오 대역 서비스(`perf/kakao-stub/mappings/*.json`, 응답 템플릿)와 앱의 `app.kakao.*` 주소 덮어쓰기, `perf/budget.js`는 VU마다 다른 카카오 ID로 OAuth 흐름 로그인(SC-004 50 VU)
- [X] T044 [P] 헌법 PATCH 개정(1.1.0 → 1.1.1): `.specify/memory/constitution.md` 원칙 IV 기준 데이터셋 문구를 "사용자 1명당 계좌 2개, 보유 종목 20건, 가이드 기록 10,000건"으로 명확히 하고 Sync Impact Report 갱신
- [X] T045 [P] 로그 마스킹에 카카오 키 패턴 추가: `RES/logback-spring.xml`에 `KakaoAK\s+\S+` → `KakaoAK ****`, 인가 코드·액세스 토큰이 로그에 없는지 `IT/user/KakaoSecretLeakIT.java`로 검증(OutputCapture)
- [X] T046 [P] 문서 갱신: `README.md`(카카오 앱 준비·환경 변수·배포 순서), `CLAUDE.md`(user 패키지, 사용자 범위 규칙: 저장소 포트는 UserId 필수), `specs/001-stock-portfolio-ai-guide/spec.md` FR-001·FR-001a에 "002에서 카카오 로그인·다중 사용자로 대체" 주석
- [X] T047 `./gradlew check`를 전체 실행하고 실패 항목을 고친다
- [ ] T048 실제 카카오 개발자 앱으로 quickstart K1~K4를 수동 검증하고, 배포 순서(운영자 첫 로그인으로 레거시 데이터 인수)를 리허설한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존성 없음
- **Foundational (Phase 2)**: Setup 이후. V9와 사용자 범위(T012~T016)가 끝나야 빌드가 통과하므로 모든 스토리를 막는다
- **US1 (Phase 3)**: Foundational 이후. MVP(실제 로그인)
- **US2 (Phase 4)**: Foundational 이후. US1과 병렬 가능하지만 E2E(T030)는 US1의 로그인 흐름이 필요하다
- **US3 (Phase 5)**: Foundational 이후. US2와 독립
- **US4 (Phase 6)**: US1(로그인·세션) 이후
- **Polish (Phase 7)**: 모든 스토리 이후

### User Story Dependency Graph

```text
Setup → Foundational ─┬─▶ US1 ──┬─▶ US4 ──┐
                      ├─▶ US2 ◀─┘(E2E만)  ├──▶ Polish
                      └─▶ US3 ────────────┘
```

### Within Each User Story

- 테스트를 먼저 작성하고 실패(Red)를 확인한다
- 마이그레이션 → 도메인 → 저장소 → 서비스 → 컨트롤러·템플릿 순서
- 체크포인트에서 `./gradlew check`를 통과시킨다

### Parallel Opportunities

- Setup: T002~T004 병렬
- Foundational 테스트 T005·T006 병렬, 구현 T008·T010·T011 병렬. T012·T013·T014는 서로 다른 기능 패키지라 병렬 가능(단, 대시보드 집계는 T014가 T012 이후)
- US1 테스트 T017~T020 병렬
- US2 테스트 T026~T030 병렬
- US3 테스트 T033·T034 병렬, **US3 전체를 US2와 병렬로 진행할 수 있다**
- US4 T040은 T039와 병렬
- Polish T043~T046 병렬

---

## Parallel Example: Foundational 사용자 범위

```bash
Task: "포트폴리오 사용자 범위 in src/main/java/com/stockmanagement/portfolio/ (T012)"
Task: "AI 키 사용자 범위 in src/main/java/com/stockmanagement/aicredential/ (T013)"
Task: "가이드 사용자 범위 in src/main/java/com/stockmanagement/guide/ (T014)"
```

## Parallel Example: User Story 2

```bash
Task: "포트폴리오 교차 사용자 IT in src/integrationTest/java/com/stockmanagement/portfolio/CrossUserPortfolioIT.java"
Task: "AI 키 교차 사용자 IT in src/integrationTest/java/com/stockmanagement/aicredential/CrossUserCredentialIT.java"
Task: "가이드 교차 사용자 IT in src/integrationTest/java/com/stockmanagement/guide/CrossUserGuideIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 격리 기반)

1. Phase 1 Setup → Phase 2 Foundational(스키마·사용자 범위)
2. Phase 3 US1 → **STOP and VALIDATE**: quickstart K1, `./gradlew check`
3. 데이터 격리는 Foundational에 이미 들어가 있으므로, 다중 사용자 공개 전에 US2(교차 사용자 테스트)를 반드시 끝낸다

### Incremental Delivery

1. US1 → 카카오 로그인(운영자 1인이 먼저 로그인해 레거시 데이터 인수)
2. US2 → 격리 검증 완료 후 다른 사용자에게 공개
3. US3 → 사용자별 한도
4. US4 → 회원 탈퇴
5. Polish → 50 사용자 성능, 헌법 문구, 문서

---

## Notes

- [P] 작업은 다른 파일이고 미완료 작업에 의존하지 않는다
- 저장소 포트의 사용자 소유 데이터 메서드는 `UserId`를 필수 인자로 받는다(누락은 컴파일 오류로 잡히게)
- 남의 항목은 항상 404이며 없는 항목과 구별되지 않는다
- 카카오 키·인가 코드·토큰은 로그·화면에 남기지 않는다
- 작업 또는 논리 단위마다 Conventional Commits로 커밋한다
