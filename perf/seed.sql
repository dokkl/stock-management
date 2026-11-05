-- 성능 기준 데이터셋(헌법 1.1.0 원칙 IV): 계좌 2개, 보유 종목 20건(제품 상한), 가이드 기록 10,000건.
-- 앱이 기동해 Flyway 마이그레이션을 끝낸 빈 DB에 넣는다. 소유자 계정은 k6 스크립트가 /setup으로 만든다.
-- 실행: docker compose exec -T db psql -U stock -d stock -v ON_ERROR_STOP=1 < perf/seed.sql
BEGIN;

-- 종목 마스터: 검색 대상 2,000종목(한글명 포함) + 보유용 20종목
INSERT INTO security (market, exchange, symbol, name_ko, name_en, currency, trading_status, updated_at)
SELECT 'KR', CASE WHEN g % 2 = 0 THEN 'KOSPI' ELSE 'KOSDAQ' END, lpad(g::text, 6, '0'),
       '성능종목' || g, 'Perf Stock ' || g, 'KRW', 'ACTIVE', now()
FROM generate_series(1, 2000) g;

INSERT INTO security (market, exchange, symbol, name_ko, name_en, currency, trading_status, updated_at)
VALUES ('KR', 'KOSPI', '005930', '삼성전자', 'Samsung Electronics', 'KRW', 'ACTIVE', now());

-- 계좌 2개
INSERT INTO account (name, created_at, updated_at) VALUES ('기준 계좌 1', now(), now()), ('기준 계좌 2', now(), now());

-- 보유 종목 20건(계좌당 10건)
INSERT INTO holding (account_id, security_id, avg_price, quantity, created_at, updated_at)
SELECT (SELECT id FROM account WHERE name = CASE WHEN s.rn <= 10 THEN '기준 계좌 1' ELSE '기준 계좌 2' END),
       s.id, 50000 + s.rn * 1000, 10, now(), now()
FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM security WHERE symbol <> '005930' ORDER BY id LIMIT 20) s;

-- 보유 종목 시세
INSERT INTO quote (security_id, price, prev_close, session_date, market_state, as_of, fetched_at)
SELECT h.security_id, h.avg_price + 500, h.avg_price, current_date, 'OPEN', now(), now()
FROM holding h;

-- 가이드 기록 10,000건: 보유 20종목 × 250일 × 하루 2슬롯, 모두 성공
INSERT INTO guide_request (security_id, holding_id, account_id, usage_date, slot, status, provider, model,
    account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot, return_rate_snapshot, currency,
    content, requested_at, completed_at)
SELECT h.security_id, h.id, h.account_id, current_date - d, s, 'SUCCEEDED', 'ANTHROPIC', 'claude-sonnet-5-5',
       a.name, h.avg_price + 500, h.avg_price, h.quantity, 0.0100, 'KRW',
       jsonb_build_object('summary', '완만한 상승 흐름입니다.', 'diagnosis', '평단가보다 조금 높습니다.',
                          'supplyDemand', '외국인 순매수가 이어집니다.', 'issues', '[]'::jsonb,
                          'overall', '기술·수급이 같은 방향이라 보유가 무난합니다.', 'action', 'HOLD',
                          'rationale', '추세가 유지되고 있습니다.', 'risks', '["업황 변동"]'::jsonb),
       (current_date - d)::timestamptz + make_interval(hours => 1 + s), (current_date - d)::timestamptz + make_interval(hours => 1 + s, secs => 20)
FROM holding h
JOIN account a ON a.id = h.account_id
CROSS JOIN generate_series(1, 250) d
CROSS JOIN generate_series(1, 2) s;

COMMIT;

ANALYZE;
