package com.stockmanagement.user.persistence;

import com.stockmanagement.user.application.UnlinkQueueEntry;
import com.stockmanagement.user.application.port.UnlinkQueue;
import com.stockmanagement.user.domain.KakaoId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUnlinkQueue implements UnlinkQueue {

    private final JdbcClient jdbc;

    JdbcUnlinkQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(KakaoId kakaoId, String reason, Instant nextAttemptAt) {
        jdbc.sql("""
                        INSERT INTO kakao_unlink_queue (kakao_id, attempts, next_attempt_at, last_error)
                        VALUES (:kakaoId, 1, :next, :reason)
                        ON CONFLICT (kakao_id) DO NOTHING
                        """)
                .param("kakaoId", kakaoId.value())
                .param("next", Timestamp.from(nextAttemptAt))
                .param("reason", truncate(reason))
                .update();
    }

    @Override
    public List<UnlinkQueueEntry> due(Instant now, int maxAttempts, int limit) {
        return jdbc.sql("""
                        SELECT kakao_id, attempts FROM kakao_unlink_queue
                        WHERE next_attempt_at <= :now AND attempts < :max
                        ORDER BY next_attempt_at LIMIT :limit
                        """)
                .param("now", Timestamp.from(now))
                .param("max", maxAttempts)
                .param("limit", limit)
                .query((rs, n) -> new UnlinkQueueEntry(new KakaoId(rs.getLong("kakao_id")), rs.getInt("attempts")))
                .list();
    }

    @Override
    public void recordFailure(KakaoId kakaoId, int attempts, String reason, Instant nextAttemptAt) {
        jdbc.sql("""
                        UPDATE kakao_unlink_queue SET attempts = :attempts, next_attempt_at = :next, last_error = :reason
                        WHERE kakao_id = :kakaoId
                        """)
                .param("attempts", attempts)
                .param("next", Timestamp.from(nextAttemptAt))
                .param("reason", truncate(reason))
                .param("kakaoId", kakaoId.value())
                .update();
    }

    @Override
    public void remove(KakaoId kakaoId) {
        jdbc.sql("DELETE FROM kakao_unlink_queue WHERE kakao_id = :kakaoId")
                .param("kakaoId", kakaoId.value())
                .update();
    }

    private static String truncate(String reason) {
        return reason == null || reason.length() <= 100 ? reason : reason.substring(0, 100);
    }
}
