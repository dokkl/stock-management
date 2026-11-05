package com.stockmanagement.support;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 통합·E2E 테스트용 데이터 삽입 헬퍼. */
public final class Fixtures {

    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-01T00:00:00Z"));

    private final JdbcClient jdbc;

    public Fixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long security(String exchange, String symbol, String nameKo, String nameEn) {
        return security(exchange, symbol, nameKo, nameEn, "ACTIVE");
    }

    public long security(String exchange, String symbol, String nameKo, String nameEn, String status) {
        boolean korea = exchange.startsWith("KOS");
        return jdbc.sql("""
                        INSERT INTO security (market, exchange, symbol, name_ko, name_en, currency, trading_status, updated_at)
                        VALUES (:market, :exchange, :symbol, :nameKo, :nameEn, :currency, :status, :now)
                        RETURNING id
                        """)
                .param("market", korea ? "KR" : "US")
                .param("exchange", exchange)
                .param("symbol", symbol)
                .param("nameKo", nameKo)
                .param("nameEn", nameEn)
                .param("currency", korea ? "KRW" : "USD")
                .param("status", status)
                .param("now", NOW)
                .query(Long.class)
                .single();
    }

    public long samsung() {
        return security("KOSPI", "005930", "삼성전자", "Samsung Electronics");
    }

    public long apple() {
        return security("NASDAQ", "AAPL", "애플", "Apple Inc.");
    }

    public long account(String name) {
        return jdbc.sql("INSERT INTO account (name, created_at, updated_at) VALUES (:name, :now, :now) RETURNING id")
                .param("name", name)
                .param("now", NOW)
                .query(Long.class)
                .single();
    }

    public long holding(long accountId, long securityId, String avgPrice, String quantity) {
        return jdbc.sql("""
                        INSERT INTO holding (account_id, security_id, avg_price, quantity, created_at, updated_at)
                        VALUES (:accountId, :securityId, :avgPrice, :quantity, :now, :now)
                        RETURNING id
                        """)
                .param("accountId", accountId)
                .param("securityId", securityId)
                .param("avgPrice", new BigDecimal(avgPrice))
                .param("quantity", new BigDecimal(quantity))
                .param("now", NOW)
                .query(Long.class)
                .single();
    }

    /** 보유 종목 n건을 계좌 1개에 빠르게 만든다(상한·성능 테스트용). */
    public void manyHoldings(long accountId, int count) {
        jdbc.sql("""
                        INSERT INTO security (market, exchange, symbol, name_ko, name_en, currency, trading_status, updated_at)
                        SELECT 'KR', 'KOSDAQ', 'S' || lpad(g::text, 5, '0'), '시드종목' || g, 'Seed ' || g, 'KRW', 'ACTIVE', :now
                        FROM generate_series(1, :count) g
                        """).param("count", count).param("now", NOW).update();
        jdbc.sql("""
                        INSERT INTO holding (account_id, security_id, avg_price, quantity, created_at, updated_at)
                        SELECT :accountId, id, 10000, 1, :now, :now FROM security WHERE name_ko LIKE '시드종목%'
                        """).param("accountId", accountId).param("now", NOW).update();
    }

    /** 시세 행을 넣는다. sessionDate는 2026-11-05, as_of와 fetched_at은 같은 시각이다. */
    public void quote(long securityId, String price, String prevClose, String marketState, Instant fetchedAt) {
        jdbc.sql("""
                        INSERT INTO quote (security_id, price, prev_close, session_date, market_state, as_of, fetched_at)
                        VALUES (:securityId, :price, :prevClose, DATE '2026-11-05', :state, :fetchedAt, :fetchedAt)
                        """)
                .param("securityId", securityId)
                .param("price", new BigDecimal(price))
                .param("prevClose", new BigDecimal(prevClose))
                .param("state", marketState)
                .param("fetchedAt", Timestamp.from(fetchedAt))
                .update();
    }
}
