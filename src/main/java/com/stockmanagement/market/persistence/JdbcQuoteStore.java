package com.stockmanagement.market.persistence;

import com.stockmanagement.market.application.HeldSecurity;
import com.stockmanagement.market.application.port.QuoteStore;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketState;
import com.stockmanagement.market.domain.Quote;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcQuoteStore implements QuoteStore {

    private final JdbcClient jdbc;

    JdbcQuoteStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<HeldSecurity> findHeldSecurities(Market market) {
        return jdbc.sql("""
                        SELECT s.id, s.symbol, q.price, q.prev_close, q.session_date, q.market_state, q.as_of, q.fetched_at
                        FROM security s
                        LEFT JOIN quote q ON q.security_id = s.id
                        WHERE s.market = :market
                          AND EXISTS (SELECT 1 FROM holding h JOIN account a ON a.id = h.account_id
                                      WHERE h.security_id = s.id AND a.deleted_at IS NULL)
                        ORDER BY s.id
                        """)
                .param("market", market.name())
                .query(JdbcQuoteStore::held)
                .list();
    }

    @Override
    public void save(Quote quote) {
        jdbc.sql("""
                        INSERT INTO quote (security_id, price, prev_close, session_date, market_state, as_of, fetched_at)
                        VALUES (:securityId, :price, :prevClose, :sessionDate, :marketState, :asOf, :fetchedAt)
                        ON CONFLICT (security_id) DO UPDATE SET
                            price = EXCLUDED.price, prev_close = EXCLUDED.prev_close,
                            session_date = EXCLUDED.session_date, market_state = EXCLUDED.market_state,
                            as_of = EXCLUDED.as_of, fetched_at = EXCLUDED.fetched_at
                        """)
                .param("securityId", quote.securityId())
                .param("price", quote.price())
                .param("prevClose", quote.prevClose())
                .param("sessionDate", quote.sessionDate())
                .param("marketState", quote.marketState().name())
                .param("asOf", Timestamp.from(quote.asOf()))
                .param("fetchedAt", Timestamp.from(quote.fetchedAt()))
                .update();
    }

    @Override
    public void updateMarketState(Market market, MarketState state) {
        jdbc.sql("""
                        UPDATE quote SET market_state = :state
                        WHERE market_state <> :state
                          AND security_id IN (SELECT id FROM security WHERE market = :market)
                        """)
                .param("state", state.name())
                .param("market", market.name())
                .update();
    }

    @Override
    public void updateSuspension(Collection<Long> suspendedIds, Collection<Long> activeIds) {
        setStatus(suspendedIds, "SUSPENDED");
        setStatus(activeIds, "ACTIVE");
    }

    private void setStatus(Collection<Long> ids, String status) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        UPDATE security SET trading_status = :status
                        WHERE id IN (:ids) AND trading_status IN ('ACTIVE', 'SUSPENDED') AND trading_status <> :status
                        """).param("status", status).param("ids", ids).update();
    }

    private static HeldSecurity held(ResultSet rs, int rowNum) throws SQLException {
        long securityId = rs.getLong("id");
        return new HeldSecurity(securityId, rs.getString("symbol"), QuoteRows.quote(rs, securityId));
    }
}
