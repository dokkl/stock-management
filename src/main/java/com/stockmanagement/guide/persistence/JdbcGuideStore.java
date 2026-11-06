package com.stockmanagement.guide.persistence;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideJob;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.GuideTarget;
import com.stockmanagement.guide.application.GuideView;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.FailureKind;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.guide.domain.GuideQuota;
import com.stockmanagement.guide.domain.GuideStatus;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.TradingStatus;
import com.stockmanagement.user.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * guide_request JDBC 구현. 슬롯 예약은 {@code ON CONFLICT ... DO NOTHING}으로 한다: 유니크 위반 예외를 쓰지 않으므로
 * 트랜잭션이 깨지지 않고, 동시 요청은 DB가 직렬화한다(research R7).
 */
@Repository
class JdbcGuideStore implements GuideStore {

    private static final String VIEW_COLUMNS = """
            g.id, g.security_id, g.holding_id, g.usage_date, g.status, g.failure_kind, g.provider, g.model,
            g.content::text AS content, g.raw_text, g.account_name_snapshot, g.price_snapshot, g.avg_price_snapshot,
            g.quantity_snapshot, g.return_rate_snapshot, g.currency, g.requested_at, g.completed_at,
            s.symbol, coalesce(s.name_ko, s.name_en) AS security_name
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcGuideStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<GuideTarget> findTarget(UserId userId, long holdingId) {
        return jdbc.sql("""
                        SELECT h.id AS holding_id, a.id AS account_id, a.name AS account_name, s.id AS security_id,
                               s.symbol, coalesce(s.name_ko, s.name_en) AS name, s.exchange, s.currency,
                               s.trading_status, h.avg_price, h.quantity, q.price, q.prev_close
                        FROM holding h
                        JOIN account a ON a.id = h.account_id AND a.deleted_at IS NULL AND a.user_id = :userId
                        JOIN security s ON s.id = h.security_id
                        LEFT JOIN quote q ON q.security_id = s.id
                        WHERE h.id = :id
                        """)
                .param("id", holdingId)
                .param("userId", userId.value())
                .query((rs, n) -> new GuideTarget(
                        rs.getLong("holding_id"),
                        rs.getLong("account_id"),
                        rs.getString("account_name"),
                        rs.getLong("security_id"),
                        rs.getString("symbol"),
                        rs.getString("name"),
                        Exchange.valueOf(rs.getString("exchange")).market(),
                        Currency.valueOf(rs.getString("currency").strip()),
                        TradingStatus.valueOf(rs.getString("trading_status")),
                        rs.getBigDecimal("avg_price"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("prev_close")))
                .optional();
    }

    @Override
    public OptionalLong findPending(UserId userId, long securityId, LocalDate usageDate) {
        return jdbc.sql("""
                        SELECT id FROM guide_request
                        WHERE user_id = :userId AND security_id = :securityId AND usage_date = :date AND status = 'PENDING'
                        ORDER BY id LIMIT 1
                        """)
                .param("userId", userId.value())
                .param("securityId", securityId)
                .param("date", usageDate)
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElse(OptionalLong.empty());
    }

    @Override
    public OptionalLong reserve(GuideReservation r) {
        for (int slot = 1; slot <= GuideQuota.DAILY_LIMIT; slot++) {
            Optional<Long> id = insert(r, slot);
            if (id.isPresent()) {
                return OptionalLong.of(id.get());
            }
        }
        return OptionalLong.empty();
    }

    private Optional<Long> insert(GuideReservation r, int slot) {
        return jdbc.sql("""
                        INSERT INTO guide_request (user_id, security_id, holding_id, account_id, usage_date, slot, status, provider,
                            account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot,
                            return_rate_snapshot, currency, requested_at)
                        VALUES (:userId, :securityId, :holdingId, :accountId, :usageDate, :slot, 'PENDING', :provider,
                            :accountName, :price, :avgPrice, :quantity, :returnRate, :currency, :requestedAt)
                        ON CONFLICT (user_id, security_id, usage_date, slot) WHERE status <> 'FAILED' DO NOTHING
                        RETURNING id
                        """)
                .param("userId", r.userId().value())
                .param("securityId", r.securityId())
                .param("holdingId", r.holdingId())
                .param("accountId", r.accountId())
                .param("usageDate", r.usageDate())
                .param("slot", slot)
                .param("provider", r.provider().name())
                .param("accountName", r.accountName())
                .param("price", r.price())
                .param("avgPrice", r.avgPrice())
                .param("quantity", r.quantity())
                .param("returnRate", r.returnRate())
                .param("currency", r.currency().name())
                .param("requestedAt", Timestamp.from(r.requestedAt()))
                .query(Long.class)
                .optional();
    }

    @Override
    public int countActive(UserId userId, long securityId, LocalDate usageDate) {
        return jdbc.sql("""
                        SELECT count(*) FROM guide_request
                        WHERE user_id = :userId AND security_id = :securityId AND usage_date = :date AND status <> 'FAILED'
                        """)
                .param("userId", userId.value())
                .param("securityId", securityId)
                .param("date", usageDate)
                .query(Integer.class)
                .single();
    }

    @Override
    public Optional<GuideJob> findJob(long id) {
        return jdbc.sql("""
                        SELECT g.id, g.user_id, g.status, g.provider, g.price_snapshot, g.avg_price_snapshot, g.quantity_snapshot,
                               g.requested_at, s.symbol, coalesce(s.name_ko, s.name_en) AS name, s.exchange, s.currency,
                               s.trading_status, q.prev_close
                        FROM guide_request g
                        JOIN security s ON s.id = g.security_id
                        LEFT JOIN quote q ON q.security_id = s.id
                        WHERE g.id = :id
                        """)
                .param("id", id)
                .query((rs, n) -> new GuideJob(
                        rs.getLong("id"),
                        new UserId(rs.getLong("user_id")),
                        GuideStatus.valueOf(rs.getString("status")),
                        AiProvider.valueOf(rs.getString("provider")),
                        rs.getString("symbol"),
                        rs.getString("name"),
                        Exchange.valueOf(rs.getString("exchange")).market(),
                        Currency.valueOf(rs.getString("currency").strip()),
                        TradingStatus.valueOf(rs.getString("trading_status")),
                        rs.getBigDecimal("price_snapshot"),
                        rs.getBigDecimal("prev_close"),
                        rs.getBigDecimal("avg_price_snapshot"),
                        rs.getBigDecimal("quantity_snapshot"),
                        rs.getTimestamp("requested_at").toInstant()))
                .optional();
    }

    @Override
    public Optional<GuideView> find(UserId userId, long id) {
        return jdbc.sql("SELECT " + VIEW_COLUMNS + " FROM guide_request g JOIN security s ON s.id = g.security_id"
                        + " WHERE g.id = :id AND g.user_id = :userId")
                .param("id", id)
                .param("userId", userId.value())
                .query(this::view)
                .optional();
    }

    @Override
    public boolean markSucceeded(long id, String model, GuideContent content, String rawText, Instant completedAt) {
        return jdbc.sql("""
                        UPDATE guide_request
                        SET status = 'SUCCEEDED', model = :model, content = CAST(:content AS jsonb), raw_text = :rawText,
                            completed_at = :completedAt
                        WHERE id = :id AND status = 'PENDING'
                        """)
                        .param("id", id)
                        .param("model", model)
                        .param("content", content == null ? null : json.writeValueAsString(content))
                        .param("rawText", rawText)
                        .param("completedAt", Timestamp.from(completedAt))
                        .update()
                > 0;
    }

    @Override
    public boolean markFailed(long id, FailureKind kind, Instant completedAt) {
        return jdbc.sql("""
                        UPDATE guide_request SET status = 'FAILED', failure_kind = :kind, completed_at = :completedAt
                        WHERE id = :id AND status = 'PENDING'
                        """)
                        .param("id", id)
                        .param("kind", kind.name())
                        .param("completedAt", Timestamp.from(completedAt))
                        .update()
                > 0;
    }

    @Override
    public int failStale(Instant requestedBefore, Instant completedAt) {
        return jdbc.sql("""
                        UPDATE guide_request
                        SET status = 'FAILED', failure_kind = 'TRANSIENT_ERROR', completed_at = :completedAt
                        WHERE status = 'PENDING' AND requested_at < :before
                        """)
                .param("before", Timestamp.from(requestedBefore))
                .param("completedAt", Timestamp.from(completedAt))
                .update();
    }

    private GuideView view(ResultSet rs, int rowNum) throws SQLException {
        long holdingId = rs.getLong("holding_id");
        Long holding = rs.wasNull() ? null : holdingId;
        String failure = rs.getString("failure_kind");
        String content = rs.getString("content");
        Timestamp completed = rs.getTimestamp("completed_at");
        return new GuideView(
                rs.getLong("id"),
                rs.getLong("security_id"),
                holding,
                rs.getString("symbol"),
                rs.getString("security_name"),
                rs.getDate("usage_date").toLocalDate(),
                GuideStatus.valueOf(rs.getString("status")),
                failure == null ? null : FailureKind.valueOf(failure),
                AiProvider.valueOf(rs.getString("provider")),
                rs.getString("model"),
                content == null ? null : json.readValue(content, GuideContent.class),
                rs.getString("raw_text"),
                rs.getString("account_name_snapshot"),
                rs.getBigDecimal("price_snapshot"),
                rs.getBigDecimal("avg_price_snapshot"),
                rs.getBigDecimal("quantity_snapshot"),
                rs.getBigDecimal("return_rate_snapshot"),
                Currency.valueOf(rs.getString("currency").strip()),
                rs.getTimestamp("requested_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}
