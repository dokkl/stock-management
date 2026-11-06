package com.stockmanagement.user.persistence;

import com.stockmanagement.user.application.port.UserStore;
import com.stockmanagement.user.domain.AppUser;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.KakaoId;
import com.stockmanagement.user.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserStore implements UserStore {

    private static final String COLUMNS = "id, kakao_id, display_name, created_at";

    private final JdbcClient jdbc;

    JdbcUserStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppUser> find(UserId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id.value())
                .query(JdbcUserStore::map)
                .optional();
    }

    @Override
    public Optional<AppUser> findByKakaoId(KakaoId kakaoId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE kakao_id = :kakaoId")
                .param("kakaoId", kakaoId.value())
                .query(JdbcUserStore::map)
                .optional();
    }

    @Override
    public Optional<UserId> claimLegacy(KakaoId kakaoId, DisplayName displayName, Instant now) {
        return jdbc.sql("""
                        UPDATE app_user SET kakao_id = :kakaoId, legacy = false, display_name = :name, last_login_at = :now
                        WHERE legacy AND kakao_id IS NULL
                        RETURNING id
                        """)
                .param("kakaoId", kakaoId.value())
                .param("name", displayName.value())
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .optional()
                .map(UserId::new);
    }

    @Override
    public UserId insert(KakaoId kakaoId, DisplayName displayName, Instant now) {
        return new UserId(jdbc.sql("""
                        INSERT INTO app_user (kakao_id, display_name, created_at, last_login_at)
                        VALUES (:kakaoId, :name, :now, :now)
                        RETURNING id
                        """)
                .param("kakaoId", kakaoId.value())
                .param("name", displayName.value())
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .single());
    }

    @Override
    public void touchLogin(UserId id, DisplayName displayName, Instant now) {
        jdbc.sql("UPDATE app_user SET display_name = :name, last_login_at = :now WHERE id = :id")
                .param("id", id.value())
                .param("name", displayName.value())
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public void lock(UserId id) {
        jdbc.sql("SELECT id FROM app_user WHERE id = :id FOR UPDATE")
                .param("id", id.value())
                .query(Long.class)
                .optional();
    }

    @Override
    public boolean delete(UserId id) {
        return jdbc.sql("DELETE FROM app_user WHERE id = :id")
                        .param("id", id.value())
                        .update()
                > 0;
    }

    private static AppUser map(ResultSet rs, int rowNum) throws SQLException {
        long kakao = rs.getLong("kakao_id");
        KakaoId kakaoId = rs.wasNull() ? null : new KakaoId(kakao);
        return new AppUser(
                new UserId(rs.getLong("id")),
                kakaoId,
                new DisplayName(rs.getString("display_name")),
                rs.getTimestamp("created_at").toInstant());
    }
}
