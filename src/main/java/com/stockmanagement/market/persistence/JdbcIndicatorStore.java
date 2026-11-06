package com.stockmanagement.market.persistence;

import com.stockmanagement.market.application.port.IndicatorStore;
import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.MarketIndicator;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIndicatorStore implements IndicatorStore {

    private final JdbcClient jdbc;

    JdbcIndicatorStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(IndicatorQuote quote) {
        jdbc.sql("""
                        INSERT INTO market_indicator_quote (indicator, value, previous_close, as_of, as_of_date, fetched_at)
                        VALUES (:indicator, :value, :previousClose, :asOf, :asOfDate, :fetchedAt)
                        ON CONFLICT (indicator) DO UPDATE SET
                            value = EXCLUDED.value, previous_close = EXCLUDED.previous_close, as_of = EXCLUDED.as_of,
                            as_of_date = EXCLUDED.as_of_date, fetched_at = EXCLUDED.fetched_at
                        """)
                .param("indicator", quote.indicator().name())
                .param("value", quote.value())
                .param("previousClose", quote.previousClose())
                .param("asOf", Timestamp.from(quote.asOf()))
                .param("asOfDate", quote.asOfDate())
                .param("fetchedAt", Timestamp.from(quote.fetchedAt()))
                .update();
    }

    @Override
    public List<IndicatorQuote> findAll() {
        return jdbc.sql("SELECT * FROM market_indicator_quote")
                .query((rs, n) -> new IndicatorQuote(
                        MarketIndicator.valueOf(rs.getString("indicator")),
                        rs.getBigDecimal("value"),
                        rs.getBigDecimal("previous_close"),
                        rs.getTimestamp("as_of").toInstant(),
                        rs.getDate("as_of_date").toLocalDate(),
                        rs.getTimestamp("fetched_at").toInstant()))
                .list();
    }
}
