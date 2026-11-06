# Quickstart: 카카오 간편로그인과 다중 사용자 지원

**Feature**: `002-kakao-login-multiuser`

기능이 처음부터 끝까지 동작함을 확인하는 실행·검증 가이드다. 경로 계약은 [contracts/web-routes.md](./contracts/web-routes.md),
카카오 연동은 [contracts/kakao-oauth.md](./contracts/kakao-oauth.md), 데이터 규칙은 [data-model.md](./data-model.md)를 본다.

## 1. 사전 준비

| 항목 | 조건 |
|---|---|
| 기능 001 실행 환경 | `specs/001-stock-portfolio-ai-guide/quickstart.md` 1장과 같음 |
| 카카오 개발자 앱 | [developers.kakao.com](https://developers.kakao.com)에서 앱 생성 → 카카오 로그인 활성화 |
| Redirect URI 등록 | `http://localhost:8080/login/oauth2/code/kakao` (운영은 실제 도메인) |
| 동의 항목 | 닉네임: 선택 동의 (다른 항목은 사용 안 함) |
| 키 | REST API 키, (켠 경우) Client Secret, 어드민 키 |

```bash
# .env 에 추가
KAKAO_CLIENT_ID=...        # REST API 키
KAKAO_CLIENT_SECRET=...    # Client Secret을 켠 경우
KAKAO_ADMIN_KEY=...        # 탈퇴 시 연결 해제용
```

## 2. 배포 순서 (기존 데이터 이전, FR-017)

1. 기능 001 데이터가 있는 DB에 새 버전을 배포한다(마이그레이션 V9·V10 적용).
2. **공지하기 전에 운영자(기존 소유자)가 가장 먼저 카카오로 로그인한다.** 대시보드에 기존 계좌·보유 종목·AI 키·가이드
   기록이 보이면 이전 완료다. 로그에 `레거시 데이터 인수: kakaoId=…`가 남는다.
3. 그다음 다른 사용자에게 공개한다.

## 3. 자동 검증

```bash
./gradlew test                 # 도메인(UserId·DisplayName·가입 전이), ArchUnit
./gradlew integrationTest      # 교차 사용자 404, 사용자별 한도·동시성, 레거시 인수 경쟁, 탈퇴 연쇄 삭제·연결 해제 재시도
./gradlew e2eTest              # WireMock 카카오로 가입·재로그인·데이터 분리·탈퇴, axe
./gradlew check
docker compose --profile perf run --rm k6   # 50 사용자 동시 활동에서 지연 예산(SC-004)
```

## 4. 수동 검증 시나리오

### K1. 가입·로그인 (US1)

1. 시크릿 창에서 `/`에 접속하면 `/login`으로 가고 "카카오로 시작하기" 버튼만 보인다(비밀번호 입력란 없음).
2. 버튼 → 카카오 로그인·동의(닉네임 선택 동의) → 빈 대시보드. 상단에 닉네임이 보인다.
3. 로그아웃 → `/login?logout`. 다시 버튼을 누르면 카카오 화면 없이(이미 로그인된 경우) 5초 안에 대시보드로 돌아오고 같은
   데이터가 보인다.
4. 카카오 동의 화면에서 "취소"하면 `/login`에 "카카오 로그인이 취소되었습니다"가 보인다.

### K2. 데이터 분리 (US2)

1. 사용자 A로 계좌 "키움 일반"과 삼성전자를 등록한다. 다른 브라우저에서 사용자 B로 로그인한다.
2. B의 대시보드는 비어 있다. B도 계좌 이름 "키움 일반"을 만들 수 있다.
3. B가 A의 보유 종목 주소(`/holdings/{A의 id}/edit`)를 직접 열면 404다.
4. A가 AI 키를 등록해도 B의 AI 설정은 "키 필요"다.

### K3. 사용자별 한도 (US3)

1. A가 삼성전자 가이드를 2회 받는다(버튼 비활성). B의 삼성전자 가이드 버튼은 "남은 2회"다.
2. A가 계좌를 2개 만든 뒤에도 B는 계좌를 만들 수 있다.

### K4. 탈퇴 (US4)

1. A가 `/settings/account`에서 "탈퇴"를 누르면 확인 대화상자가 나온다. 확인하면 `/login?withdrawn`으로 간다.
2. 카카오 계정 > 연결된 서비스 관리에서 이 앱이 사라졌는지 확인한다.
3. A의 카카오 계정으로 다시 로그인하면 빈 대시보드(새 사용자)다.

## 5. 기대 결과 요약

| 시나리오 | 기대 |
|---|---|
| 처음 로그인 | 30초 이내 대시보드(SC-001) |
| 재로그인 | 5초 이내(SC-002) |
| 교차 사용자 접근 | 모두 404, 데이터 불변(SC-003) |
| 같은 사용자 동시 가이드 20건 × 100회 | 비실패 2건 이하(SC-005) |
| 탈퇴 후 개인 데이터 | 0건(SC-006) |
