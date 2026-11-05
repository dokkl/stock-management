package com.stockmanagement.portfolio.persistence;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.common.domain.KstDate;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Quote;
import com.stockmanagement.market.domain.QuoteStatus;
import com.stockmanagement.market.domain.TradingStatus;
import com.stockmanagement.market.persistence.QuoteRows;
import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.portfolio.application.DashboardView;
import com.stockmanagement.portfolio.application.DashboardView.AccountView;
import com.stockmanagement.portfolio.application.DashboardView.HoldingView;
import com.stockmanagement.user.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** account LEFT JOIN holding JOIN security LEFT JOIN quote LEFT JOIN 오늘 가이드 사용 수 — 단일 쿼리(N+1 없음). */
@Repository
class DashboardJdbcQuery implements DashboardQuery {

    private static final String SELECT = """
            SELECT a.id AS account_id, a.name AS account_name,
                   h.id AS holding_id, h.avg_price, h.quantity,
                   s.id AS security_id, s.exchange, s.symbol, s.name_ko, s.name_en, s.currency, s.trading_status,
                   q.price, q.prev_close, q.session_date, q.market_state, q.as_of, q.fetched_at,
                   coalesce(gq.used, 0) AS guides_used
            FROM account a
            LEFT JOIN holding h ON h.account_id = a.id
            LEFT JOIN security s ON s.id = h.security_id
            LEFT JOIN quote q ON q.security_id = s.id
            LEFT JOIN (SELECT security_id, count(*) AS used FROM guide_request
                       WHERE user_id = :userId AND usage_date = :today AND status <> 'FAILED' GROUP BY security_id) gq
                   ON gq.security_id = s.id
            WHERE a.deleted_at IS NULL AND a.user_id = :userId
            """;
    private static final String ORDER =
            " ORDER BY a.name COLLATE \"C\", a.id, coalesce(s.name_ko, s.name_en) COLLATE \"C\", h.id";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration staleAfter;

    DashboardJdbcQuery(JdbcClient jdbc, Clock clock, @Value("${app.quote.stale-after}") Duration staleAfter) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    @Override
    public DashboardView load(UserId userId) {
        return new DashboardView(group(jdbc.sql(SELECT + ORDER)
                .param("today", today())
                .param("userId", userId.value())
                .query(rowMapper())
                .list()));
    }

    @Override
    public Optional<AccountView> findAccount(UserId userId, long accountId) {
        List<Row> rows = jdbc.sql(SELECT + " AND a.id = :id" + ORDER)
                .param("id", accountId)
                .param("today", today())
                .param("userId", userId.value())
                .query(rowMapper())
                .list();
        return group(rows).stream().findFirst();
    }

    @Override
    public Optional<HoldingView> findHolding(UserId userId, long holdingId) {
        return jdbc.sql(SELECT + " AND h.id = :id")
                .param("id", holdingId)
                .param("today", today())
                .param("userId", userId.value())
                .query(rowMapper())
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

    /** 가이드 한도 집계 날짜(KST). */
    private LocalDate today() {
        return KstDate.today(clock);
    }

    /** 시세 상태는 조회 시각 기준이므로 쿼리마다 시각을 한 번 정해 모든 행에 같은 기준을 쓴다. */
    private RowMapper<Row> rowMapper() {
        Instant now = clock.instant();
        return (rs, rowNum) -> {
            long accountId = rs.getLong("account_id");
            long holdingId = rs.getLong("holding_id");
            HoldingView holding = rs.wasNull() ? null : holding(rs, accountId, holdingId, now);
            return new Row(accountId, rs.getString("account_name"), holding);
        };
    }

    private HoldingView holding(ResultSet rs, long accountId, long holdingId, Instant now) throws SQLException {
        String nameKo = rs.getString("name_ko");
        long securityId = rs.getLong("security_id");
        Quote quote = QuoteRows.quote(rs, securityId);
        return new HoldingView(
                holdingId,
                accountId,
                securityId,
                Exchange.valueOf(rs.getString("exchange")),
                rs.getString("symbol"),
                nameKo != null ? nameKo : rs.getString("name_en"),
                Currency.valueOf(rs.getString("currency").strip()),
                TradingStatus.valueOf(rs.getString("trading_status")),
                rs.getBigDecimal("avg_price"),
                rs.getBigDecimal("quantity"),
                quote,
                QuoteStatus.of(quote, now, staleAfter),
                rs.getInt("guides_used"));
    }

    private record Row(long accountId, String accountName, HoldingView holding) {}
}
