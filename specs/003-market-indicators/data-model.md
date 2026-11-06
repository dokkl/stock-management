# Data Model: 대시보드 시장 지표 요약

## MarketIndicator (도메인 enum, 고정 8개)

| 코드 | 이름 | 순서 | 단위 | 갱신 방식 | 제공처 키 |
|---|---|---|---|---|---|
| KOSPI | 코스피 | 1 | pt | REALTIME | 토스 `KOSPI` |
| KOSDAQ | 코스닥 | 2 | pt | REALTIME | 토스 `KOSDAQ` |
| USD_KRW | 원·달러 환율 | 3 | 원 | REALTIME | 토스 환율 USD→KRW |
| NASDAQ | 나스닥 종합 | 4 | pt | DAILY_CLOSE | FRED `NASDAQCOM` |
| SP500 | S&P 500 | 5 | pt | DAILY_CLOSE | FRED `SP500` |
| DOW | 다우존스 | 6 | pt | DAILY_CLOSE | FRED `DJIA` |
| WTI | WTI 유가 | 7 | 달러/배럴 | DAILY_CLOSE | FRED `DCOILWTICO` |
| VIX | VIX 변동성 | 8 | pt | DAILY_CLOSE | FRED `VIXCLS` |

제공처 키는 어댑터가 가진다(도메인은 제공처를 모른다).

## IndicatorQuote (도메인 record)

| 필드 | 타입 | 규칙 |
|---|---|---|
| indicator | MarketIndicator | 필수 |
| value | BigDecimal | 필수, > 0 |
| previousClose | BigDecimal | null 가능(등락 계산 불가 → 값만 표시) |
| asOf | Instant | 값의 기준 시각(미국 지표는 기준일 00:00 KST 대신 날짜로 표시) |
| asOfDate | LocalDate | 미국 지표 기준일(관측치 date). 국내 지표는 asOf의 KST 날짜 |
| fetchedAt | Instant | 마지막 수신 시각(지연 판단) |

파생: `change() = value - previousClose`, `changeRate() = change / previousClose`(소수 4자리, HALF_UP), `direction()`(UP/DOWN/FLAT, 기존 `Direction`).

## IndicatorStatus (도메인 enum)

`LIVE`(국내 장중, 수신 10분 이내) · `STALE`(국내 장중, 10분 초과) · `CLOSED`(국내 장 외) · `DAILY_CLOSE`(미국 지표) · `UNAVAILABLE`(행 없음).

## 테이블 `market_indicator_quote` (V12)

| 컬럼 | 타입 | 제약 |
|---|---|---|
| indicator | VARCHAR(16) | PK, CHECK IN (8개 코드) |
| value | NUMERIC(20,4) | NOT NULL, > 0 |
| previous_close | NUMERIC(20,4) | NULL |
| as_of | TIMESTAMPTZ | NOT NULL |
| as_of_date | DATE | NOT NULL |
| fetched_at | TIMESTAMPTZ | NOT NULL |

갱신은 `INSERT … ON CONFLICT (indicator) DO UPDATE`. 사용자 범위 없음(공유 데이터).
