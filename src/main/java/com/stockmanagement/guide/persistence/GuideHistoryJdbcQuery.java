package com.stockmanagement.guide.persistence;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideHistoryEntry;
import com.stockmanagement.guide.application.GuideHistoryPage;
import com.stockmanagement.guide.application.port.GuideHistoryQuery;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.user.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * 종목별 성공 가이드 기록. ix_guide_history(security_id, requested_at DESC) WHERE status = 'SUCCEEDED'를 쓰는 단일
 * 쿼리이며, 한 건 더 읽어 다음 페이지 유무를 판단한다. 계좌는 소프트 삭제라 JOIN이 항상 성립한다.
 */
@Repository
class GuideHistoryJdbcQuery implements GuideHistoryQuery {

    static final int HEADLINE_MAX = 160;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    GuideHistoryJdbcQuery(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<GuideHistoryPage> find(UserId userId, long securityId, int page) {
        Optional<String[]> security = jdbc.sql(
                        "SELECT symbol, coalesce(name_ko, name_en) AS name FROM security WHERE id = :id")
                .param("id", securityId)
                .query((rs, n) -> new String[] {rs.getString("symbol"), rs.getString("name")})
                .optional();
        if (security.isEmpty()) {
            return Optional.empty();
        }
        int safePage = Math.max(0, page);
        List<GuideHistoryEntry> rows = jdbc.sql("""
                        SELECT g.id, g.requested_at, g.provider, g.model, g.content::text AS content, g.raw_text,
                               g.account_name_snapshot, a.deleted_at IS NOT NULL AS account_deleted,
                               g.holding_id IS NULL AS holding_ended, g.price_snapshot, g.avg_price_snapshot,
                               g.quantity_snapshot, g.return_rate_snapshot, g.currency
                        FROM guide_request g
                        JOIN account a ON a.id = g.account_id
                        WHERE g.user_id = :userId AND g.security_id = :securityId AND g.status = 'SUCCEEDED'
                        ORDER BY g.requested_at DESC, g.id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("userId", userId.value())
                .param("securityId", securityId)
                .param("limit", GuideHistoryPage.PAGE_SIZE + 1)
                .param("offset", safePage * GuideHistoryPage.PAGE_SIZE)
                .query(this::entry)
                .list();
        boolean hasNext = rows.size() > GuideHistoryPage.PAGE_SIZE;
        return Optional.of(new GuideHistoryPage(
                securityId,
                security.get()[1],
                security.get()[0],
                hasNext ? rows.subList(0, GuideHistoryPage.PAGE_SIZE) : rows,
                safePage,
                hasNext));
    }

    private GuideHistoryEntry entry(ResultSet rs, int rowNum) throws SQLException {
        String raw = rs.getString("content");
        GuideContent content = raw == null ? null : json.readValue(raw, GuideContent.class);
        return new GuideHistoryEntry(
                rs.getLong("id"),
                rs.getTimestamp("requested_at").toInstant(),
                AiProvider.valueOf(rs.getString("provider")),
                rs.getString("model"),
                content == null ? null : content.action(),
                headline(content, rs.getString("raw_text")),
                rs.getString("account_name_snapshot"),
                rs.getBoolean("account_deleted"),
                rs.getBoolean("holding_ended"),
                rs.getBigDecimal("price_snapshot"),
                rs.getBigDecimal("avg_price_snapshot"),
                rs.getBigDecimal("quantity_snapshot"),
                rs.getBigDecimal("return_rate_snapshot"),
                Currency.valueOf(rs.getString("currency").strip()));
    }

    /** 결론 한 줄: 종합 판단 → 요약 → 원문 앞부분 순서. 길면 줄임표로 자른다. */
    static String headline(GuideContent content, String rawText) {
        String text = content == null ? rawText : (content.isComprehensive() ? content.overall() : content.summary());
        if (text == null) {
            return "";
        }
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= HEADLINE_MAX ? oneLine : oneLine.substring(0, HEADLINE_MAX - 1) + "…";
    }
}
