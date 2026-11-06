# Research: 카카오 간편로그인과 다중 사용자 지원

**Feature**: `002-kakao-login-multiuser` | **Date**: 2026-11-05

기술 스택은 기능 001과 같다(사용자 결정). 아래는 이번 기능에서 정한 사항이다.

## R1. 카카오 로그인 연동 방식

- **Decision**: Spring Security **OAuth2 Client**(`oauth2Login`)에 카카오를 커스텀 공급자로 등록한다(Authorization Code).
  - 공급자 설정: authorization-uri `https://kauth.kakao.com/oauth/authorize`, token-uri `https://kauth.kakao.com/oauth/token`,
    user-info-uri `https://kapi.kakao.com/v2/user/me`, user-name-attribute `id`,
    client-authentication-method `client_secret_post`(카카오 콘솔에서 Client Secret을 켠 경우), scope `profile_nickname`.
  - 리다이렉트 주소: `{baseUrl}/login/oauth2/code/kakao`(카카오 콘솔에 등록).
  - 앱 키는 환경 변수 `KAKAO_CLIENT_ID`(REST API 키), `KAKAO_CLIENT_SECRET`으로 받는다.
- **Rationale**: Spring Boot 표준 스타터라 기술 스택을 바꾸지 않는다. `state` 생성·검증(위조·재사용 차단, FR-005),
  세션 고정 방지, 실패 처리 흐름이 기본 제공된다.
- **Alternatives considered**:
  - 카카오 JavaScript SDK 로그인: 클라이언트 토큰을 서버가 다시 검증해야 하고 CSP(`script-src 'self'`)와 충돌한다.
  - 직접 구현한 OAuth 클라이언트: `state`·토큰 교환·오류 처리를 다시 만들어야 한다.

## R2. 사용자 식별과 가입

- **Decision**: 카카오 회원번호(`id`, 64비트 정수)를 `app_user.kakao_id`(유니크)로 쓴다. 커스텀 `OAuth2UserService`가 사용자
  정보 응답을 받아 `UserService.signIn(kakaoId, nickname)`을 호출한다. 처음 보는 ID면 가입, 있으면 닉네임·마지막 로그인
  시각만 갱신한다. 세션에는 앱 사용자 ID를 담은 `AppUserPrincipal`을 둔다.
  - 닉네임: `kakao_account.profile.nickname`(선택 동의). 없거나 비어 있으면 표시 이름은 "카카오 사용자"(FR-004, Edge Case).
    길이는 40자로 자른다.
  - 이메일·전화번호는 요청하지 않는다.
- **Rationale**: 카카오가 앱 단위로 보장하는 고유·불변 식별자다. 닉네임은 바뀔 수 있으므로 표시 용도로만 쓴다.
- **Alternatives considered**: 이메일을 식별자로 쓰기 — 동의 항목이 늘고 바뀔 수 있으며 FR-004에 어긋난다.

## R3. 기존 비밀번호 인증 제거와 세션

- **Decision**: `formLogin`, `/setup`, 로그인 잠금(`LoginLockPolicy`), `owner` 테이블을 제거하고 `oauth2Login`으로 바꾼다.
  - 로그인 화면 `/login`에는 "카카오로 시작하기" 버튼(`/oauth2/authorization/kakao` 링크)만 둔다.
  - 실패·취소(`error=access_denied`)는 `/login?error=cancelled|failed`로 돌아와 안내한다.
  - 세션: 비활성 7일 유지(기존 설정), 로그인 성공 시 세션 ID 재발급(기본 동작), CSRF 유지, 로그아웃은 POST `/logout`.
  - htmx 요청의 미인증 응답(401 + `HX-Redirect: /login`)은 그대로 둔다.
- **Rationale**: FR-001·FR-006. 비밀번호 저장소가 없어지므로 Argon2·잠금 정책도 필요 없다.

## R4. 소유권 모델과 격리

- **Decision**: 사용자 소유 루트에 `user_id`를 둔다: `account`, `ai_credential`, `guide_request`. `holding`은 `account`를
  통해 소유된다(별도 컬럼 없음, 조회 시 JOIN). 공유 테이블(`security`, `quote`, `market_token`)은 그대로다.
  - 현재 사용자 해석은 웹 계층에서 한 번(`@AuthenticationPrincipal AppUserPrincipal` → `UserId`) 하고 애플리케이션
    서비스·저장소 포트에 **필수 인자**로 넘긴다.
  - 모든 단건 조회·변경 쿼리는 `id = :id AND user_id = :userId`(holding은 account JOIN) 조건이다. 결과가 없으면 기존과
    같은 `ResourceNotFoundException`(404)이라 남의 항목 존재 여부가 드러나지 않는다(FR-009).
  - 백그라운드 작업: 시세 스케줄러는 모든 사용자 보유 종목 합집합(공유), `GuideRunner`는 가이드 요청 행의 `user_id`로 그
    사용자의 키를 꺼낸다, 정체 정리는 전역.
- **Rationale**: 루트 3곳만 소유권을 갖게 해 조건 누락 지점을 줄인다. 404 통일은 열거 공격을 막는다.
- **Alternatives considered**:
  - PostgreSQL Row Level Security: 세션 변수 설정을 모든 연결에 걸어야 하고 JDBC 풀·백그라운드 작업과 맞추기 번거롭다.
  - 모든 테이블에 `user_id` 비정규화: holding까지 넣으면 계좌 이동 등에서 일관성 위험이 생긴다.

## R5. 사용자별 한도

- **Decision**:
  - 계좌 2개: `count(account WHERE user_id = ? AND deleted_at IS NULL)`. 계좌명 유일성은 `(user_id, name) WHERE deleted_at IS NULL`.
  - 보유 20개: 사용자 계좌들의 holding 합계.
  - 가이드 하루 2회: 슬롯 유니크 인덱스를 `(user_id, security_id, usage_date, slot) WHERE status <> 'FAILED'`로 바꾼다.
    예약 SQL의 `ON CONFLICT` 대상도 같이 바꾼다. 같은 사용자의 여러 계좌는 합산(FR-014).
  - 같은 사용자의 계좌·보유 동시 생성 경쟁은 사용자 행 잠금(`SELECT … FROM app_user WHERE id = ? FOR UPDATE`)으로
    직렬화한다(사용자 단위라 경합이 거의 없다).
- **Rationale**: 기존 DB 보장 방식(R7 of 001)을 사용자 차원으로 확장할 뿐이다.

## R6. 기존 데이터의 첫 사용자 이전 (FR-017)

- **Decision**: V9 마이그레이션에서
  1. `app_user`를 만들고(`kakao_id`는 NULL 허용 + 부분 유니크),
  2. `owner` 행이 있으면 **레거시 사용자 1행**(`kakao_id NULL`, `legacy = true`, `selected_ai_provider` 승계)을 넣고,
  3. 기존 `account`·`ai_credential`·`guide_request`의 `user_id`를 그 사용자로 채운 뒤 NOT NULL로 바꾸고,
  4. `owner` 테이블을 삭제한다.
  - 첫 카카오 로그인 처리(`UserService.signIn`): 해당 카카오 ID 사용자가 없으면 먼저
    `UPDATE app_user SET kakao_id = :kid, legacy = false, … WHERE legacy AND kakao_id IS NULL RETURNING id`를 시도한다.
    1행이면 그 사용자가 기존 데이터를 인수한 것이고, 0행이면 새 사용자를 INSERT한다.
  - 동시 첫 로그인: UPDATE가 레거시 행을 잠그므로 한 트랜잭션만 성공하고, 다른 쪽은 0행을 보고 새 사용자로 가입한다
    (Edge Case). 인수 시 `INFO` 감사 로그에 카카오 ID를 남긴다.
- **Rationale**: 데이터를 복사하지 않고 소유자 행 하나만 바꿔 원자적으로 끝난다. 레거시 데이터가 없으면 레거시 행도 없다.
- **Alternatives considered**: 첫 로그인 시 모든 테이블의 `user_id`를 일괄 UPDATE — 여러 테이블 갱신과 종료 상태 불변
  트리거(guide_request)와 충돌한다.

## R7. 회원 탈퇴와 카카오 연결 해제

- **Decision**:
  - `POST /settings/account/withdraw`(확인 대화상자 후) → 한 트랜잭션에서 `DELETE FROM app_user WHERE id = ?`.
    `account`·`ai_credential`·`guide_request`는 `ON DELETE CASCADE`, `holding`은 account에서 연쇄 삭제된다.
    guide_request 불변 트리거는 UPDATE만 막으므로 DELETE는 통과한다(holding 연쇄 삭제의 SET NULL도 기존 예외로 허용).
  - 커밋 후 카카오 연결 해제: `POST https://kapi.kakao.com/v1/user/unlink`, 헤더 `Authorization: KakaoAK {KAKAO_ADMIN_KEY}`,
    본문 `target_id_type=user_id&target_id={kakaoId}`. 사용자 액세스 토큰을 저장하지 않아도 된다.
  - 실패하면 `kakao_unlink_queue`(kakao_id, attempts, next_attempt_at)에 넣고 스케줄러가 지수 백오프로 재시도한다(최대 10회).
  - 진행 중인 가이드는 행이 사라져 `markSucceeded`가 0행이 되므로 결과가 버려진다(Edge Case).
  - 세션은 즉시 무효화하고 로그인 화면으로 보낸다.
- **Rationale**: FR-015·FR-016, SC-006. 어드민 키 방식이 토큰 보관보다 단순하고 안전하다.
- **Alternatives considered**: 사용자 액세스 토큰으로 연결 해제 — 토큰을 암호화 저장·갱신해야 한다.

## R8. 성능 검증과 테스트 전략

- **Decision**:
  - k6 시드: 기준 데이터셋 사용자 1명(계좌 2·보유 20·가이드 기록 1만) + 계좌 1·보유 5의 사용자 49명. k6 VU 50명이 각자
    다른 사용자로 로그인한다. 로그인은 테스트 전용 세션 발급 대신, 시드한 사용자에 대해 WireMock 카카오 대역을 쓰는
    프로필로 OAuth 흐름을 그대로 탄다.
  - 통합 테스트: Spring Security Test의 `oauth2Login().oauth2User(...)`로 `AppUserPrincipal`을 주입하는 헬퍼(`as(user)`)를
    둔다. 모든 사용자 범위 라우트에 "다른 사용자 → 404, 데이터 불변" 케이스를 추가한다.
  - E2E: 카카오 authorize·token·user/me를 WireMock으로 대체한다. authorize 스텁은 응답 템플릿으로 `state`를 그대로
    돌려주는 302를 보낸다. `loginAs(kakaoId, nickname)` 헬퍼로 기존 `setupAndLogin`을 대체한다.
  - 인덱스: `account(user_id)`, `ai_credential(user_id, provider)` 유니크, `guide_request(user_id, security_id, requested_at DESC)`
    (기록 조회), 슬롯 유니크(R5).
- **Rationale**: SC-003(격리 0건 노출)은 라우트별 교차 사용자 테스트로, SC-004는 k6 50 VU로 검증한다.
