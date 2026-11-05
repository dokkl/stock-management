package com.stockmanagement.market.persistence;

import com.stockmanagement.market.domain.MarketState;
import com.stockmanagement.market.domain.Quote;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/** quote 컬럼(price, prev_close, session_date, market_state, as_of, fetched_at)을 Quote로 읽는다. LEFT JOIN으로 없으면 null. */
public final class QuoteRows {

    private QuoteRows() {}

    public static Quote quote(ResultSet rs, long securityId) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        if (price == null) {
            return null;
        }
        return new Quote(
                securityId,
                price,
                rs.getBigDecimal("prev_close"),
                rs.getDate("session_date").toLocalDate(),
                MarketState.valueOf(rs.getString("market_state")),
                rs.getTimestamp("as_of").toInstant(),
                rs.getTimestamp("fetched_at").toInstant());
    }
}
