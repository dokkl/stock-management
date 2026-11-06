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

## Cboe 지수 일별 종가 CSV (S&P 500·VIX)

```text
GET https://cdn-api.cboe.com/api/global/us_indices/daily_prices/SPX_History.csv   → DATE,SPX
GET https://cdn-api.cboe.com/api/global/us_indices/daily_prices/VIX_History.csv   → DATE,OPEN,HIGH,LOW,CLOSE
```

날짜 `MM/dd/yyyy`, 마지막 열이 종가. 마지막 두 행을 최신·직전 값으로 쓴다. 설정 `app.cboe.base-url`, `app.cboe.timeout`.

## 야후 파이낸스 차트 API (WTI 근월물 선물, 비공식)

```text
GET https://query1.finance.yahoo.com/v8/finance/chart/CL=F?range=10d&interval=1d
→ {"chart":{"result":[{"timestamp":[…],"indicators":{"quote":[{"close":[…]}]}}]}}
```

종가가 null이 아닌 마지막 두 봉(날짜는 뉴욕 기준 봉 시작일). User-Agent 헤더 필요. 설정 `app.yahoo.base-url`, `app.yahoo.timeout`.

## 제공처 선택

지표별로 모든 제공처(Cboe·야후·FRED)를 조회해 가장 최신 날짜를 쓴다. 같은 날짜면 Cboe·야후(@Order 1·2)가 FRED(@Order 10)보다 앞선다.
