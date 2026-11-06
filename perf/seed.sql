-- 성능 기준 데이터셋(헌법 1.1.1 원칙 IV, 기능 002): 카카오 사용자 50명.
--   - 기준 사용자 1명(kakao 900001): 계좌 2개, 보유 종목 20건(제품 상한), 가이드 기록 10,000건
--   - 일반 사용자 49명(kakao 900002~900050): 계좌 1개, 보유 종목 5건, 가이드 기록 10건
-- 앱이 기동해 Flyway 마이그레이션을 끝낸 빈 DB에 넣는다. k6는 perf/kakao-stub 대역으로 각 사용자에 카카오 로그인한다.
-- 실행: docker compose exec -T db psql -U stock -d stock -v ON_ERROR_STOP=1 < perf/seed.sql
BEGIN;

-- 종목 마스터: 검색 대상 2,000종목
INSERT INTO security (market, exchange, symbol, name_ko, name_en, currency, trading_status, updated_at)
SELECT 'KR', CASE WHEN g % 2 = 0 THEN 'KOSPI' ELSE 'KOSDAQ' END, lpad(g::text, 6, '0'),
       '성능종목' || g, 'Perf Stock ' || g, 'KRW', 'ACTIVE', now()
FROM generate_series(1, 2000) g;

-- 사용자 50명
INSERT INTO app_user (kakao_id, display_name, created_at, last_login_at)
SELECT 900000 + u, '성능사용자' || u, now(), now() FROM generate_series(1, 50) u;

-- 계좌: 기준 사용자 2개, 나머지 1개씩
INSERT INTO account (user_id, name, created_at, updated_at)
SELECT id, '기준 계좌 ' || n, now(), now()
FROM app_user CROSS JOIN generate_series(1, 2) n WHERE kakao_id = 900001;
INSERT INTO account (user_id, name, created_at, updated_at)
SELECT id, '일반 계좌', now(), now() FROM app_user WHERE kakao_id > 900001;

-- 보유 종목: 기준 사용자 20건(계좌당 10), 나머지 5건
INSERT INTO holding (account_id, security_id, avg_price, quantity, created_at, updated_at)
SELECT a.id, s.id, 50000 + s.id * 10, 10, now(), now()
FROM account a
JOIN app_user u ON u.id = a.user_id AND u.kakao_id = 900001
JOIN LATERAL (SELECT id FROM security ORDER BY id OFFSET (CASE WHEN a.name = '기준 계좌 1' THEN 0 ELSE 10 END) LIMIT 10) s ON true;
INSERT INTO holding (account_id, security_id, avg_price, quantity, created_at, updated_at)
SELECT a.id, s.id, 50000 + s.id * 10, 3, now(), now()
FROM account a
JOIN app_user u ON u.id = a.user_id AND u.kakao_id > 900001
JOIN LATERAL (SELECT id FROM security ORDER BY id OFFSET 20 + ((u.kakao_id - 900002) * 5) % 1900 LIMIT 5) s ON true;

-- 시세: 보유 종목 합집합
INSERT INTO quote (security_id, price, prev_close, session_date, market_state, as_of, fetched_at)
SELECT DISTINCT h.security_id, 60000, 59500, current_date, 'OPEN', now(), now() FROM holding h;

-- 가이드 기록: 기준 사용자 20종목 × 250일 × 2슬롯 = 10,000건, 일반 사용자 5종목 × 1일 × 2슬롯 = 10건
INSERT INTO guide_request (user_id, security_id, holding_id, account_id, usage_date, slot, status, provider, model,
    account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot, return_rate_snapshot, currency,
    content, requested_at, completed_at)
SELECT a.user_id, h.security_id, h.id, h.account_id, current_date - d, s, 'SUCCEEDED', 'ANTHROPIC', 'claude-sonnet-5-5',
       a.name, 60000, h.avg_price, h.quantity, 0.0100, 'KRW',
       jsonb_build_object('summary', '완만한 상승 흐름입니다.', 'diagnosis', '평단가보다 조금 높습니다.',
                          'supplyDemand', '외국인 순매수가 이어집니다.', 'issues', '[]'::jsonb,
                          'overall', '기술·수급이 같은 방향이라 보유가 무난합니다.', 'action', 'HOLD',
                          'rationale', '추세가 유지되고 있습니다.', 'risks', '["업황 변동"]'::jsonb),
       (current_date - d)::timestamptz + make_interval(hours => 1 + s),
       (current_date - d)::timestamptz + make_interval(hours => 1 + s, secs => 20)
FROM holding h
JOIN account a ON a.id = h.account_id
JOIN app_user u ON u.id = a.user_id
CROSS JOIN LATERAL generate_series(1, CASE WHEN u.kakao_id = 900001 THEN 250 ELSE 1 END) d
CROSS JOIN generate_series(1, 2) s;

COMMIT;

ANALYZE;
