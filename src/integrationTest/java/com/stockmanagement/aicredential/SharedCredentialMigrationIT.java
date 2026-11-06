package com.stockmanagement.aicredential;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V11 마이그레이션: 사용자별 AI 키를 관리자 공용 키로 바꾼다. 관리자(ADMIN_KAKAO_IDS)의 키·선택만 남기고, 관리자 지정이 없으면 가장 먼저
 * 만들어진 사용자(기능 001의 기존 운영자)의 것을 남긴다.
 */
class SharedCredentialMigrationIT extends IntegrationTestBase {

    private static final String DB = "shared_credential_migration_it";

    private DriverManagerDataSource freshDatabase() {
        jdbc.sql("DROP DATABASE IF EXISTS " + DB).update();
        jdbc.sql("CREATE DATABASE " + DB).update();
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DB + "$1"),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static void migrate(DriverManagerDataSource ds, String target, String adminKakaoIds) {
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .placeholders(Map.of("admin_kakao_ids", adminKakaoIds))
                .target(target)
                .load()
                .migrate();
    }

    @AfterEach
    void dropDatabase() {
        jdbc.sql("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)").update();
    }

    private static long user(JdbcClient db, Long kakaoId, boolean legacy, String selected) {
        return db.sql("""
                        INSERT INTO app_user (kakao_id, display_name, legacy, selected_ai_provider, created_at)
                        VALUES (:k, '사용자', :legacy, :selected, now()) RETURNING id
                        """)
                .param("k", kakaoId)
                .param("legacy", legacy)
                .param("selected", selected)
                .query(Long.class)
                .single();
    }

    private static void key(JdbcClient db, long userId, String provider, String last4, String verifiedAt) {
        db.sql("""
                        INSERT INTO ai_credential (user_id, provider, ciphertext, iv, key_version, last4, verified_at,
                            created_at, updated_at)
                        VALUES (:u, :p, 'c'::bytea, decode('000000000000000000000000', 'hex'), 1, :last4,
                            CAST(:at AS timestamptz), now(), now())
                        """)
                .param("u", userId)
                .param("p", provider)
                .param("last4", last4)
                .param("at", verifiedAt)
                .update();
    }

    private static List<String> keys(JdbcClient db) {
        return db.sql("SELECT provider || ':' || last4 FROM ai_credential ORDER BY provider")
                .query(String.class)
                .list();
    }

    private static String selected(JdbcClient db) {
        return db.sql("SELECT selected_provider FROM ai_settings")
                .query(String.class)
                .optional()
                .orElse(null);
    }

    @Test
    void keepsOnlyAdminKeysAndSelection() {
        DriverManagerDataSource ds = freshDatabase();
        migrate(ds, "10", "");
        JdbcClient db = JdbcClient.create(ds);
        long admin1 = user(db, 9001L, false, "OPENAI");
        long admin2 = user(db, 9002L, false, "ANTHROPIC");
        long member = user(db, 1001L, false, "ANTHROPIC");
        key(db, admin1, "OPENAI", "a1op", "2026-11-01T00:00:00Z");
        key(db, admin2, "ANTHROPIC", "a2an", "2026-11-01T00:00:00Z");
        key(db, admin2, "OPENAI", "a2op", "2026-11-02T00:00:00Z");
        key(db, member, "ANTHROPIC", "mbr1", "2026-11-03T00:00:00Z");

        migrate(ds, "11", "9001, 9002");

        assertThat(keys(db)).as("관리자 키만, 같은 공급자는 최근 확인한 키").containsExactly("ANTHROPIC:a2an", "OPENAI:a2op");
        assertThat(selected(db)).as("먼저 가입한 관리자의 선택").isEqualTo("OPENAI");
        assertThat(db.sql("SELECT column_name FROM information_schema.columns"
                                + " WHERE table_name IN ('ai_credential', 'app_user')"
                                + " AND column_name IN ('user_id', 'selected_ai_provider')")
                        .query(String.class)
                        .list())
                .as("사용자별 키·선택 컬럼 제거")
                .isEmpty();
    }

    @Test
    void withoutAdminKeepsFirstUserKeys() {
        DriverManagerDataSource ds = freshDatabase();
        migrate(ds, "10", "");
        JdbcClient db = JdbcClient.create(ds);
        long legacy = user(db, null, true, "ANTHROPIC");
        long member = user(db, 1001L, false, "OPENAI");
        key(db, legacy, "ANTHROPIC", "lgcy", "2026-11-01T00:00:00Z");
        key(db, member, "OPENAI", "mbr1", "2026-11-03T00:00:00Z");

        migrate(ds, "11", "");

        assertThat(keys(db)).containsExactly("ANTHROPIC:lgcy");
        assertThat(selected(db)).isEqualTo("ANTHROPIC");
    }

    @Test
    void selectionFallsBackToRemainingKeyAndEmptyWhenNoKeys() {
        DriverManagerDataSource ds = freshDatabase();
        migrate(ds, "10", "");
        JdbcClient db = JdbcClient.create(ds);
        long admin = user(db, 9001L, false, "ANTHROPIC");
        key(db, admin, "OPENAI", "a1op", "2026-11-01T00:00:00Z");

        migrate(ds, "11", "9001");

        assertThat(selected(db)).as("선택한 공급자의 키가 없으면 남은 키").isEqualTo("OPENAI");

        db.sql("DELETE FROM ai_credential").update();
        assertThat(db.sql("SELECT count(*) FROM ai_settings").query(Long.class).single())
                .isEqualTo(1);
    }
}
