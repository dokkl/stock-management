# Quickstart: 대시보드 시장 지표 요약

## 준비

- 기존 실행 환경(`specs/001…/quickstart.md`, `infra/README.md`)과 같다.
- FRED API 키: https://fred.stlouisfed.org/docs/api/api_key.html 에서 발급 → 로컬 `.env`의 `FRED_API_KEY`,
  운영 SSM `/stock-management/prod/FRED_API_KEY`.

## 자동 검증

```bash
./gradlew test --tests 'com.stockmanagement.market.domain.*'   # 등락·상태 계산
./gradlew integrationTest --tests '*Indicator*'                 # 토스·FRED WireMock, 저장, 라우트
./gradlew e2eTest --tests '*MarketIndicatorsE2E'                # 카드 표시, 모바일, axe
./gradlew check
```

## 수동 확인 (운영)

1. https://stock.seeplus.net 대시보드 상단에 8개 카드가 순서대로 보인다.
2. 국내 장중: 코스피·코스닥·환율에 "HH:mm 기준", 2분 안에 값이 바뀐다. 장 외에는 "장 마감".
3. 미국 지표 카드에 "MM-DD 종가"와 출처 표기(FRED)가 보인다.
4. 모바일(390px)에서 가로 넘침이 없다.
5. 하단에 "ⓒ 2026. hoon All rights reserved."가 보인다.
