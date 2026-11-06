# Contract: Web Routes (변경분)

**Feature**: `002-kakao-login-multiuser`

기능 001 계약(`specs/001-stock-portfolio-ai-guide/contracts/web-routes.md`)에서 바뀌는 부분만 적는다.

## 인증

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| ~~GET/POST~~ | ~~`/setup`~~ | 제거 | 404 |
| GET | `/login` | "카카오로 시작하기" 버튼만 있는 화면. `?error=cancelled` / `?error=failed` / `?logout` / `?withdrawn` 안내 | 200 |
| ~~POST~~ | ~~`/login`~~ | 비밀번호 로그인 제거 | — |
| GET | `/oauth2/authorization/kakao` | 카카오 인증 화면으로 302(`state` 포함) | 302 |
| GET | `/login/oauth2/code/kakao` | 카카오 콜백. `state` 검증 → 토큰 교환 → 사용자 정보 → 가입/로그인 → `/` | 302 `/` / 302 `/login?error=…` |
| POST | `/logout` | 이 서비스 세션 종료(CSRF 필요) | 302 `/login?logout` |

- 미인증 요청: 일반 요청은 302 `/login`, htmx 요청은 401 + `HX-Redirect: /login`(기존 유지).
- 카카오 취소(`error=access_denied`) → `/login?error=cancelled`, 그 외 실패(`state` 불일치·토큰/사용자 정보 오류) →
  `/login?error=failed`. 실패 응답·로그에 인가 코드·토큰을 남기지 않는다.

## 회원 정보·탈퇴 (신규)

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| GET | `/settings/account` | 표시 이름, 가입일, 탈퇴 버튼 | 200 |
| POST | `/settings/account/withdraw` | 확인 대화상자 후 호출. 데이터 삭제·세션 무효화·카카오 연결 해제 | 302 `/login?withdrawn` (htmx: 200 + `HX-Redirect`) |

## 공통 헤더

- 상단 네비게이션: `대시보드 · AI 설정 · {표시 이름} · 로그아웃`. 표시 이름은 `/settings/account` 링크다.

## 소유권 규칙 (모든 기존 경로에 적용, FR-008·FR-009)

아래 경로는 **현재 사용자 소유 항목만** 대상으로 한다. 다른 사용자 소유 id는 존재하지 않는 id와 똑같이 **404**이며,
응답 본문·시간에 차이를 두지 않고 데이터를 바꾸지 않는다.

| 경로 | 소유 판단 |
|---|---|
| `/accounts/{id}`(PUT/DELETE), `/accounts/{id}/edit`, `/accounts/{id}/header`, `POST /accounts/{id}/holdings` | `account.user_id` |
| `/holdings/{id}`(GET/PUT/DELETE), `/holdings/{id}/edit`, `/holdings/{id}/guides`, `/holdings/{id}/guide-button` | holding → account.user_id |
| `/guides/{id}`, `/guides/{id}/status` | `guide_request.user_id` |
| `/settings/ai/**` | 현재 사용자 키·선택만 다룬다(경로에 사용자 id 없음) |
| `/securities/{id}/guides` | 종목은 공유, **기록은 현재 사용자 것만** 보인다 |
| `/`, `/fragments/quotes` | 현재 사용자 계좌·보유만 |
| `/securities/search` | 공유(소유권 없음) |

## 한도 응답 (FR-013·FR-014)

기능 001과 같은 422/429 응답이며, 판단 기준만 사용자 단위로 바뀐다.
