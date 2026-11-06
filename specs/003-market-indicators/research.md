# Research: 대시보드 시장 지표 요약

## R1. 국내 지수(코스피·코스닥) — 토스 시장 지표

- **Decision**: `GET /api/v1/market-indicators/prices?symbols=KOSPI,KOSDAQ`로 현재가(`lastPrice`, `timestamp`)를,
  `GET /api/v1/market-indicators/{symbol}/candles?interval=1d&count=2`로 전일 종가를 받는다(오늘 봉이 있으면 두 번째 봉,
  없으면 첫 번째 봉의 `closePrice`). Rate Limits Group은 `MARKET_INDICATOR`, `MARKET_INDICATOR_CHART`(공시 한도 미확인 →
  초당 2회로 보수적 설정).
- **Rationale**: 현재가 응답에 전일 대비 값이 없어 등락 계산에 전일 종가가 필요하다. 전일 종가는 하루에 한 번만 바뀌므로
  (국내 영업일 기준 날짜가 바뀔 때) 캔들은 날짜가 바뀐 경우에만 다시 받는다.
- **Alternatives**: 지수 ETF 가격 — 지수 값이 아니어서 제외.

## R2. 원·달러 환율 — 토스 환율

- **Decision**: `GET /api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW`의 `midRate`(매매기준율)를 값으로, 같은 API에
  `dateTime`=직전 국내 영업일 15:30 KST를 주어 받은 `midRate`를 전일 기준값으로 쓴다. 기준 시각은 `validFrom`. 그룹 `MARKET_INFO`.
- **Rationale**: `rate`는 매수 환율(스프레드 포함)이라 시장 표기와 다르다. 응답에 전일 값이 없어 특정 시점 조회로 비교값을 만든다.
- **Alternatives**: `rateChangeType`만 사용 — 등락 폭·등락률을 보일 수 없어 제외.

## R3. 미국 지표 — FRED

- **Decision**: `GET https://api.stlouisfed.org/fred/series/observations?series_id={id}&api_key=…&file_type=json&sort_order=desc&limit=10`.
  시리즈: 나스닥 종합 `NASDAQCOM`, S&P 500 `SP500`, 다우 `DJIA`, WTI `DCOILWTICO`(달러/배럴), VIX `VIXCLS`. 값이 `"."`(결측)인
  관측치는 건너뛰고, 값이 있는 최신 관측치를 현재 값, 그다음 값이 있는 관측치를 전일 값으로 쓴다. 기준일은 관측치 `date`.
  1시간마다 갱신(발표 시각이 시리즈마다 달라 하루 한 번 고정 시각보다 안전). FRED 한도 120회/분 대비 시간당 5회.
- **Rationale**: 공식·무료·IP 제한 없음, 실제 지수 값. 실시간이 아님은 화면에 "MM-DD 종가"로 표기한다.
- **출처 표기**: S&P·다우 지수 시리즈는 저작권 고지가 있으므로 지표 영역에 "미국 지표 출처: FRED, Federal Reserve Bank of St. Louis"를 표시한다.
- **Alternatives**: 비공식 시세(야후 등) — 약관·차단 위험. ETF 대체 — 지수 값이 아님.

## R4. 갱신 주기와 상태

- **Decision**:
  - 국내 지수·환율: 국내 장중(시장 캘린더 `OPEN`) 1분마다, 그 밖에는 30분마다 1회(마감 값 확정·환율 변동 반영).
  - FRED: 1시간마다.
  - 상태: 국내 지표는 장중 + 마지막 수신 10분 이내 `LIVE`, 10분 초과 `STALE`(지연), 장 외 `CLOSED`(마감). 미국 지표는 항상
    `DAILY_CLOSE`. 행이 없으면 `UNAVAILABLE`.
- **Rationale**: SC-002(2분 이내)를 1분 주기 + 화면 60초 폴링으로 만족. 기존 `MarketCalendarCache`를 재사용한다.

## R5. 저장과 화면

- **Decision**: 지표별 최신 1행(`market_indicator_quote`, PK = 지표 코드)을 upsert. 대시보드는 첫 렌더에 저장값을 포함하고,
  지표 영역은 `GET /fragments/indicators`를 htmx `every 60s`로 폴링한다(로그인 필요, 사용자 범위 없음).
- **Rationale**: 외부 장애와 화면 분리(FR-009), 사용자 수와 무관한 호출량(FR-008).

## R6. 네트워크

- **Decision**: 토스 호출은 기존 `TossHttpClient`(고정 IP 프록시 `TOSS_PROXY_URL`)를 그대로 쓴다. FRED는 별도 `RestClient`로 직접
  호출한다(프록시는 토스 도메인만 허용). FRED API 키는 `FRED_API_KEY`(SSM SecureString, 로그·화면 비노출).

## R3-개정. 최신 종가 제공처 (2026-11-06)

- **문제**: FRED는 시리즈마다 발표가 늦다(S&P 500 다음 날 오후, VIX 다음 날 아침, WTI는 미국 에너지청 기준 1~2 영업일 지연).
  사용자가 "모두 최신 정보"를 요구했다.
- **Decision**: 미국 지표마다 여러 제공처를 조회해 **가장 최신 날짜**의 관측치를 쓴다(같은 날짜면 우선순위가 앞선 제공처).
  - S&P 500·VIX: Cboe 공식 일별 종가 CSV(`https://cdn-api.cboe.com/api/global/us_indices/daily_prices/{SPX|VIX}_History.csv`, 장 마감 당일 저녁 갱신, 키 불필요).
  - WTI: 야후 파이낸스 차트 API 근월물 선물 `CL=F`(비공식, 거의 실시간). 실패하면 FRED 현물가(`DCOILWTICO`)로 대체.
  - 나스닥 종합·다우: FRED가 당일 밤에 갱신되어 그대로 쓴다(다른 제공처에서도 FRED가 대체로 남는다).
- **Alternatives**: Stooq CSV — API 제거·브라우저 검증으로 사용 불가. Cboe의 나스닥 종합(COMP) 파일 — 접근 거부(403).
- **위험**: 야후 API는 비공식이라 차단·형식 변경 가능 → FRED 대체로 화면은 유지된다(기준일만 늦어짐).
