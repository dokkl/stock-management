package com.stockmanagement.portfolio.persistence;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.TradingStatus;
import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.portfolio.application.DashboardView;
import com.stockmanagement.portfolio.application.DashboardView.AccountView;
import com.stockmanagement.portfolio.application.DashboardView.HoldingView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** account LEFT JOIN holding JOIN security 단일 쿼리(N+1 없음). */
@Repository
class DashboardJdbcQuery implements DashboardQuery {

    private static final String SELECT = """
            SELECT a.id AS account_id, a.name AS account_name,
                   h.id AS holding_id, h.avg_price, h.quantity,
                   s.id AS security_id, s.exchange, s.symbol, s.name_ko, s.name_en, s.currency, s.trading_status
            FROM account a
            LEFT JOIN holding h ON h.account_id = a.id
            LEFT JOIN security s ON s.id = h.security_id
            WHERE a.deleted_at IS NULL
            """;
    private static final String ORDER =
            " ORDER BY a.name COLLATE \"C\", a.id, coalesce(s.name_ko, s.name_en) COLLATE \"C\", h.id";

    private final JdbcClient jdbc;

    DashboardJdbcQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DashboardView load() {
        return new DashboardView(
                group(jdbc.sql(SELECT + ORDER).query(DashboardJdbcQuery::row).list()));
    }

    @Override
    public Optional<AccountView> findAccount(long accountId) {
        List<Row> rows = jdbc.sql(SELECT + " AND a.id = :id" + ORDER)
                .param("id", accountId)
                .query(DashboardJdbcQuery::row)
                .list();
        return group(rows).stream().findFirst();
    }

    @Override
    public Optional<HoldingView> findHolding(long holdingId) {
        return jdbc.sql(SELECT + " AND h.id = :id")
                .param("id", holdingId)
                .query(DashboardJdbcQuery::row)
                .optional()
                .map(Row::holding);
    }

    private static List<AccountView> group(List<Row> rows) {
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, List<HoldingView>> holdings = new LinkedHashMap<>();
        for (Row row : rows) {
            names.putIfAbsent(row.accountId(), row.accountName());
            List<HoldingView> list = holdings.computeIfAbsent(row.accountId(), id -> new ArrayList<>());
            if (row.holding() != null) {
                list.add(row.holding());
            }
        }
        return names.entrySet().stream()
                .map(e -> new AccountView(e.getKey(), e.getValue(), List.copyOf(holdings.get(e.getKey()))))
                .toList();
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        long accountId = rs.getLong("account_id");
        long holdingId = rs.getLong("holding_id");
        HoldingView holding = rs.wasNull() ? null : holding(rs, accountId, holdingId);
        return new Row(accountId, rs.getString("account_name"), holding);
    }

    private static HoldingView holding(ResultSet rs, long accountId, long holdingId) throws SQLException {
        String nameKo = rs.getString("name_ko");
        return new HoldingView(
                holdingId,
                accountId,
                rs.getLong("security_id"),
                Exchange.valueOf(rs.getString("exchange")),
                rs.getString("symbol"),
                nameKo != null ? nameKo : rs.getString("name_en"),
                Currency.valueOf(rs.getString("currency").strip()),
                TradingStatus.valueOf(rs.getString("trading_status")),
                rs.getBigDecimal("avg_price"),
                rs.getBigDecimal("quantity"));
    }

    private record Row(long accountId, String accountName, HoldingView holding) {}
}
