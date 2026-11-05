# Implementation Plan: 카카오 간편로그인과 다중 사용자 지원

**Branch**: `008-kakao-login-multiuser` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-kakao-login-multiuser/spec.md`

## Summary

비밀번호 기반 1인 소유자 구조(기능 001)를 **카카오 간편로그인 + 다중 사용자** 구조로 바꾼다.

- **인증**: Spring Security OAuth2 Client로 카카오 로그인(Authorization Code)을 붙인다. 첫 로그인 시 자동 가입한다.
  비밀번호 로그인·최초 설정·로그인 잠금은 제거한다(research R1~R3).
- **소유권**: `owner`(1행)를 `app_user`(N행)로 바꾸고, 계좌·AI 키·가이드 요청에 `user_id`를 둔다. 모든 조회·변경은
  현재 사용자 조건을 붙이고, 남의 항목은 404로 응답한다(R4).
- **한도**: 계좌 2·보유 20·종목별 하루 가이드 2회를 사용자 단위로 센다. 가이드 슬롯 유니크 인덱스에 `user_id`를 더한다(R5).
- **기존 데이터**: 마이그레이션이 기존 소유자 데이터를 "주인 없는 레거시 사용자"로 옮겨 두고, 첫 카카오 로그인이 그
  행을 원자적으로 차지한다(R6).
- **탈퇴**: 사용자 데이터를 연쇄 삭제하고 카카오 어드민 키로 연결을 해제한다. 실패하면 재시도 대기열에 남긴다(R7).

기술 스택은 기능 001과 같다(Java 25, Spring Boot 4.1, Thymeleaf + htmx, PostgreSQL 18). 추가 의존성은 Spring Boot
스타터 `spring-boot-starter-oauth2-client` 하나다.

## Technical Context

**Language/Version**: Java 25 (LTS)

**Primary Dependencies**: Spring Boot 4.1.2(Web MVC, Thymeleaf, Security, **OAuth2 Client**, Data JPA, Validation),
Spring AI 2.0.2, Flyway, htmx 2.x — 기능 001과 동일하며 OAuth2 Client 스타터만 추가

**Storage**: PostgreSQL 18 (마이그레이션 V9부터)

**Testing**: JUnit 6, AssertJ, Spring Boot Test, Spring Security Test(`oauth2Login()`), Testcontainers, WireMock(카카오
인증·사용자 정보·연결 해제 대역, 응답 템플릿으로 `state` 왕복), ArchUnit, Playwright + axe, JaCoCo, k6

**Target Platform**: Linux 컨테이너(Docker / Docker Compose), 데스크톱 우선 웹

**Project Type**: web-service (서버 렌더링 웹 앱)

**Performance Goals**:
- 헌법 원칙 IV 지연 예산 유지(단건 200ms, 목록 300ms, 쓰기 500ms p95)
- SC-004: 동시 활동 사용자 50명에서 위 예산 유지
- SC-002: 재로그인 후 대시보드 5초 이내(카카오 왕복 포함)

**Constraints**:
- 카카오 필수 동의는 사용자 식별자뿐이고, 닉네임은 선택 동의(FR-004)
- 사용자 간 데이터 노출 0건(SC-003): 모든 저장소 메서드가 사용자 범위를 받는다
- 공유 데이터(종목·시세·장 운영·수급)는 사용자 범위 밖이다(FR-011)
- 카카오 앱 키·시크릿·어드민 키는 운영 설정(환경 변수)으로 받고 로그·화면에 남기지 않는다

**Scale/Scope**:
- 공개 가입(FR-003), 동시 활동 50명 목표
- 사용자당 계좌 2·보유 20, 가이드 기록 누적
- 화면: 로그인(카카오 버튼), 회원 정보·탈퇴 화면 추가. 기존 화면은 사용자 범위로 동작

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

헌법 v1.1.0 기준. **Phase 0 전: PASS / Phase 1 후 재평가: PASS**

| 원칙 / 게이트 | 계획상 준수 방법 | 상태 |
|---|---|---|
| I. 린트·포맷 경고 0, 복잡도 ≤10 | 기존 Spotless·Checkstyle·Error Prone 그대로 | ✅ |
| I. 도메인 분리·단방향 의존 | 새 `user` 기능 패키지(`domain`·`application`·`persistence`·`web`·`adapter/kakao`). ArchUnit 규칙 그대로 적용 | ✅ |
| I. 경계 검증 | 카카오 사용자 정보 응답은 어댑터에서 검증(식별자 필수, 닉네임 길이 제한) | ✅ |
| I. 오류 삼킴 금지 | 카카오 로그인 실패·연결 해제 실패는 명시적 결과로 처리하고 로그·재시도 대기열에 남긴다 | ✅ |
| II. TDD·계층별 테스트 | 소유권 격리는 라우트별 "남의 항목 → 404" 통합 테스트로 고정(SC-003). E2E는 WireMock 카카오로 가입·재로그인·탈퇴 | ✅ |
| II. 결정성 | 카카오는 WireMock 대역, 시계는 `MutableClock` | ✅ |
| II. 커버리지 | 기존 기준 유지 | ✅ |
| III. UX 일관성·접근성 | 카카오 버튼은 카카오 디자인 가이드 색(노랑)을 토큰으로 정의하고 대비 4.5:1 확인. 탈퇴는 공용 확인 대화상자 | ✅ |
| IV. 지연 예산 | 모든 사용자 조회에 `user_id` 선행 인덱스. k6 시드에 사용자 50명 추가 | ✅ |
| IV. 페이지네이션 ≤100 | 사용자당 보유 20건 상한으로 대시보드도 100건 이하 | ✅ |
| 게이트 5. 보안 | OAuth2 `state` 검증(위조·재사용 차단), 세션 고정 방지(로그인 시 세션 재발급), 카카오 키 비노출, 남의 리소스 404 | ✅ |

**주석**: 헌법 원칙 IV의 기준 데이터셋(계좌 2·보유 20·가이드 기록 1만)은 다중 사용자에서 "사용자 1명이 보는 범위"로
해석한다. 성능 시드는 이 데이터셋을 가진 사용자 1명 + 일반 사용자 49명으로 구성한다(research R8). 헌법 문구를 "사용자당"으로
명확히 하는 PATCH 개정을 후속으로 제안한다(Complexity Tracking 아님).

## Project Structure

### Documentation (this feature)

```text
specs/002-kakao-login-multiuser/
├── plan.md              # 이 문서
├── research.md          # Phase 0: R1~R8
├── data-model.md        # Phase 1: app_user, 소유권 컬럼, 인덱스, 마이그레이션 순서
├── quickstart.md        # Phase 1: 실행·검증 가이드
├── contracts/
│   ├── web-routes.md    # 바뀌는 화면·경로, 소유권 규칙
│   └── kakao-oauth.md   # 카카오 외부 연동 계약(인증·사용자 정보·연결 해제)
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

기존 단일 Gradle 모듈에 `user` 기능 패키지를 더하고 `auth`의 비밀번호 관련 코드를 걷어 낸다.

```text
src/main/java/com/stockmanagement/
├── user/                                   # 새 기능: 사용자·카카오 로그인·탈퇴
│   ├── domain/        # AppUser, KakaoId, DisplayName
│   ├── application/   # UserService(가입·로그인 시 upsert, 레거시 데이터 인수), WithdrawalService,
│   │                  #   CurrentUser(UserId 해석), port/UserStore, port/KakaoUnlinker
│   ├── persistence/   # JdbcUserStore
│   ├── adapter/kakao/ # KakaoOAuth2UserService(사용자 정보 → AppUserPrincipal), KakaoUnlinkClient(어드민 키)
│   └── web/           # LoginController(카카오 버튼), AccountSettingsController(회원 정보·탈퇴)
├── auth/              # 제거 대상: SetupService, LoginAttemptService, LoginLockPolicy, Owner*, SetupController,
│                      #   SetupRequiredFilter, OwnerAuthenticationProvider (HtmxAwareAuthenticationEntryPoint는 user/web로 이동)
├── common/config/SecurityConfig.java       # formLogin → oauth2Login
├── portfolio/ market/ aicredential/ guide/ # 포트 메서드에 UserId 인자 추가, 쿼리에 user_id 조건
src/main/resources/
├── db/migration/V9__app_user.sql           # app_user 생성, 레거시 사용자 이전, user_id 컬럼·인덱스, owner 제거
├── db/migration/V10__kakao_unlink_queue.sql
└── templates/auth/login.html, settings/account.html

src/integrationTest/.../support/            # oauth2Login 기반 user(…) 헬퍼, 사용자 2명 픽스처
src/e2eTest/.../support/E2ETestBase         # setupAndLogin → WireMock 카카오로 loginAs(kakaoId)
```

**Structure Decision**: 사용자·인증은 독립 기능 패키지 `user`로 둔다. 다른 기능은 `user.domain.UserId`(값 객체)만
알고, 현재 사용자 해석은 웹 계층에서 한 번 해서 애플리케이션 서비스에 인자로 넘긴다. 애플리케이션·도메인 계층은
Spring Security에 의존하지 않는다.

## Complexity Tracking

> 헌법 위반이 없어 비워 둔다.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (없음) | | |

## Risks & Open Items

| 위험 | 영향 | 대응 |
|---|---|---|
| 배포 직후 운영자가 아닌 사람이 먼저 로그인 | 기존 데이터가 그 사람에게 이전됨(FR-017 가정) | quickstart에 배포 순서 명시. 레거시 이전 시 감사 로그(카카오 ID) 기록 |
| 공개 가입으로 시세 호출량 증가 | 토스증권 호출 한도 근접 | 시세 갱신은 보유 종목 합집합(중복 제거). 갱신 대상 수를 로그·지표로 노출 |
| 카카오 연결 해제 실패 | 카카오 쪽에 연결이 남음 | 재시도 대기열(V10)과 스케줄러. 서비스 데이터 삭제는 먼저 끝낸다 |
| 소유권 조건 누락 | 사용자 간 데이터 노출 | 저장소 포트가 UserId를 필수 인자로 받게 하고, 라우트별 교차 사용자 404 테스트 + ArchUnit으로 포트 시그니처 점검 |
