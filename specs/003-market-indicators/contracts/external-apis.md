# Contract: External APIs (003 시장 지표)

## 토스증권 Open API (고정 IP 프록시 경유, 기존 토큰 재사용)

```text
GET /api/v1/market-indicators/prices?symbols=KOSPI,KOSDAQ          [MARKET_INDICATOR]
→ {"result":[{"symbol":"KOSPI","timestamp":"2026-11-06T13:20:00+09:00","lastPrice":"2812.45"}, …]}

GET /api/v1/market-indicators/{symbol}/candles?interval=1d&count=2  [MARKET_INDICATOR_CHART]
→ {"result":{"candles":[{"timestamp":"…","closePrice":"2790.10", …}, …]}}   최신순

GET /api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW[&dateTime=…]  [MARKET_INFO]
→ {"result":{"midRate":"1375","validFrom":"…","rateChangeType":"UP", …}}
```

- `timestamp`가 null이면 값 미제공으로 보고 저장하지 않는다.
- 4xx/5xx·타임아웃: 해당 지표만 이번 갱신에서 건너뛰고 경고 로그(값 유지).

## FRED (직접 호출)

```text
GET https://api.stlouisfed.org/fred/series/observations
    ?series_id={NASDAQCOM|SP500|DJIA|DCOILWTICO|VIXCLS}&api_key={FRED_API_KEY}&file_type=json&sort_order=desc&limit=10
→ {"observations":[{"date":"2026-11-05","value":"23051.12"},{"date":"2026-11-04","value":"."}, …]}
```

- `value == "."`는 결측 → 건너뛴다. 값 있는 관측치가 없으면 저장하지 않는다.
- API 키는 쿼리 파라미터라 요청 URL을 로그에 남기지 않는다(예외 메시지 포함).
- 설정: `app.fred.base-url`(기본 `https://api.stlouisfed.org`), `app.fred.api-key`(`FRED_API_KEY`), `app.fred.timeout`.
  키가 비어 있으면 FRED 갱신을 건너뛴다(로컬·테스트).
