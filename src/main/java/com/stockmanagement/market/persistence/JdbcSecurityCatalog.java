package com.stockmanagement.market.persistence;

import com.stockmanagement.market.application.port.SecurityCatalog;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.Security;
import com.stockmanagement.market.domain.SecurityListing;
import com.stockmanagement.market.domain.TradingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSecurityCatalog implements SecurityCatalog {

    private static final String COLUMNS = "id, exchange, symbol, name_ko, name_en, trading_status";
    private static final double SIMILARITY_THRESHOLD = 0.2;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    JdbcSecurityCatalog(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Security> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM security WHERE id = :id")
                .param("id", id)
                .query(JdbcSecurityCatalog::map)
                .optional();
    }

    @Override
    public List<Security> search(String query, Market market, int limit) {
        String prefix = escapeLike(query.toUpperCase(java.util.Locale.ROOT)) + "%";
        String contains = "%" + escapeLike(query) + "%";
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM security
                         WHERE trading_status <> 'DELISTED'
                           AND (CAST(:market AS VARCHAR) IS NULL OR market = :market)
                           AND (upper(symbol) LIKE :prefix
                                OR name_ko ILIKE :contains OR name_en ILIKE :contains
                                OR similarity(coalesce(name_ko, ''), :q) > :threshold
                                OR similarity(coalesce(name_en, ''), :q) > :threshold)
                         ORDER BY (upper(symbol) = upper(:q)) DESC,
                                  (upper(symbol) LIKE :prefix) DESC,
                                  greatest(similarity(coalesce(name_ko, ''), :q),
                                           similarity(coalesce(name_en, ''), :q)) DESC,
                                  symbol
                         LIMIT :limit
                        """)
                .param("market", market == null ? null : market.name())
                .param("prefix", prefix)
                .param("contains", contains)
                .param("q", query)
                .param("threshold", SIMILARITY_THRESHOLD)
                .param("limit", limit)
                .query(JdbcSecurityCatalog::map)
                .list();
    }

    @Override
    public boolean isEmpty() {
        return !jdbc.sql("SELECT EXISTS (SELECT 1 FROM security)")
                .query(Boolean.class)
                .single();
    }

    @Override
    public void upsert(List<SecurityListing> listings, Instant syncedAt) {
        Timestamp now = Timestamp.from(syncedAt);
        jdbcTemplate.batchUpdate("""
                INSERT INTO security (market, exchange, symbol, name_ko, name_en, currency, trading_status, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (exchange, symbol) DO UPDATE SET
                    name_ko = EXCLUDED.name_ko,
                    name_en = EXCLUDED.name_en,
                    trading_status = CASE WHEN security.trading_status = 'DELISTED' THEN 'ACTIVE'
                                          ELSE security.trading_status END,
                    updated_at = EXCLUDED.updated_at
                """, listings, 500, (ps, l) -> {
            ps.setString(1, l.exchange().market().name());
            ps.setString(2, l.exchange().name());
            ps.setString(3, l.symbol());
            ps.setString(4, l.nameKo());
            ps.setString(5, l.nameEn());
            ps.setString(6, l.exchange().market().currency().name());
            ps.setTimestamp(7, now);
        });
    }

    @Override
    public int delistMissing(Exchange exchange, Instant syncedAt) {
        return jdbc.sql("""
                        UPDATE security SET trading_status = 'DELISTED', updated_at = :now
                        WHERE exchange = :exchange AND updated_at < :now AND trading_status <> 'DELISTED'
                        """)
                .param("exchange", exchange.name())
                .param("now", Timestamp.from(syncedAt))
                .update();
    }

    private static Security map(ResultSet rs, int rowNum) throws SQLException {
        return new Security(
                rs.getLong("id"),
                Exchange.valueOf(rs.getString("exchange")),
                rs.getString("symbol"),
                rs.getString("name_ko"),
                rs.getString("name_en"),
                TradingStatus.valueOf(rs.getString("trading_status")));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
