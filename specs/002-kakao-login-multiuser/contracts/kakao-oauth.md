# Contract: Kakao 외부 연동

**Feature**: `002-kakao-login-multiuser`

카카오 로그인 REST API 중 이 기능이 쓰는 부분. 운영 값은 환경 변수로 받고, 테스트는 같은 경로를 WireMock으로 대체한다
(주소는 설정으로 바꿀 수 있어야 한다). 구현 시 카카오 개발자 문서로 필드명을 다시 확인한다.

## 설정

| 환경 변수 | 용도 | 비고 |
|---|---|---|
| `KAKAO_CLIENT_ID` | REST API 키(client_id) | 필수 |
| `KAKAO_CLIENT_SECRET` | Client Secret | 콘솔에서 켠 경우 필수 |
| `KAKAO_ADMIN_KEY` | 연결 해제(어드민 키) | 탈퇴 시 사용. 로그·화면 비노출 |
| (설정) `app.kakao.auth-base-url` / `app.kakao.api-base-url` | 기본 `https://kauth.kakao.com` / `https://kapi.kakao.com` | 테스트에서 WireMock 주소 |

카카오 콘솔 설정: 플랫폼 Web 사이트 도메인, Redirect URI `{서비스 주소}/login/oauth2/code/kakao`, 동의 항목 "닉네임"(선택 동의).

## 1. 인가 요청 (브라우저 리다이렉트)

```text
GET {auth}/oauth/authorize?response_type=code&client_id=…&redirect_uri=…&scope=profile_nickname&state=…
→ 302 {redirect_uri}?code=…&state=…            (동의 완료)
→ 302 {redirect_uri}?error=access_denied&state=… (사용자 취소)
```

- `state`는 서비스가 만들고 콜백에서 세션 값과 비교한다(불일치 시 실패, FR-005).

## 2. 토큰 교환 (서버 → 카카오)

```text
POST {auth}/oauth/token   (application/x-www-form-urlencoded)
  grant_type=authorization_code, client_id, client_secret?, redirect_uri, code
→ 200 { "access_token", "token_type": "bearer", "expires_in", "refresh_token", ... }
→ 4xx { "error", "error_description" }  → 로그인 실패
```

- 액세스·리프레시 토큰은 사용자 정보 조회에만 쓰고 **저장하지 않는다**.

## 3. 사용자 정보 (서버 → 카카오)

```text
GET {api}/v2/user/me   Authorization: Bearer {access_token}
→ 200 {
    "id": 1234567890,                                   // 필수. 앱 단위 고유 회원번호 → app_user.kakao_id
    "kakao_account": { "profile": { "nickname": "홍길동" } }   // 선택 동의. 없을 수 있음
  }
```

- 검증: `id`가 없거나 숫자가 아니면 실패. 닉네임은 공백 제거 후 40자 이내로 자르고, 없으면 "카카오 사용자".

## 4. 연결 해제 (서버 → 카카오, 탈퇴 시)

```text
POST {api}/v1/user/unlink   (application/x-www-form-urlencoded)
  Authorization: KakaoAK {KAKAO_ADMIN_KEY}
  target_id_type=user_id&target_id={kakao_id}
→ 200 { "id": {kakao_id} }       성공
→ 4xx/5xx/시간 초과              실패 → kakao_unlink_queue에 넣고 재시도
```

- 이미 연결이 끊긴 사용자에 대한 오류(카카오가 "앱에 연결되지 않은 사용자"로 응답)는 성공으로 본다.

## 테스트 대역 (WireMock)

| 경로 | 스텁 |
|---|---|
| `GET /oauth/authorize` | 302 → `{redirect_uri}?code=test-code-{kakaoId}&state={{request.query.state}}` (응답 템플릿) |
| `POST /oauth/token` | 본문의 code로 사용자 구분 → `access_token=token-{kakaoId}` |
| `GET /v2/user/me` | `Authorization: Bearer token-{kakaoId}` → `{"id": kakaoId, "kakao_account": {"profile": {"nickname": …}}}` |
| `POST /v1/user/unlink` | 200 / 503(재시도 검증) |
