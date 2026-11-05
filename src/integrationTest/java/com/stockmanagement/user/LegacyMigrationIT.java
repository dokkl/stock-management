package com.stockmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.support.IntegrationTestBase;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V9 마이그레이션(T006, research R6): 기능 001 소유자 데이터를 레거시 사용자로 옮기고 owner 테이블을 없앤다. */
class LegacyMigrationIT extends IntegrationTestBase {

    private static final String DB = "legacy_migration_it";

    private DriverManagerDataSource freshDatabase() {
        jdbc.sql("DROP DATABASE IF EXISTS " + DB).update();
        jdbc.sql("CREATE DATABASE " + DB).update();
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DB + "$1"),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static void migrate(DriverManagerDataSource ds, String target) {
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    @AfterEach
    void dropDatabase() {
        jdbc.sql("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)").update();
    }

    @Test
    void ownerDataMovesToLegacyUser() {
        DriverManagerDataSource ds = freshDatabase();
        migrate(ds, "8");
        JdbcClient db = JdbcClient.create(ds);
        db.sql("INSERT INTO owner (id, password_hash, selected_ai_provider, created_at, updated_at)"
                        + " VALUES (1, 'x', 'OPENAI', now(), now())")
                .update();
        db.sql("INSERT INTO security (market, exchange, symbol, name_ko, currency, updated_at)"
                        + " VALUES ('KR', 'KOSPI', '005930', '삼성전자', 'KRW', now())")
                .update();
        long account = db.sql(
                        "INSERT INTO account (name, created_at, updated_at) VALUES ('키움', now(), now()) RETURNING id")
                .query(Long.class)
                .single();
        db.sql(
                        "INSERT INTO ai_credential (provider, ciphertext, iv, key_version, last4, verified_at, created_at, updated_at)"
                                + " VALUES ('OPENAI', 'c'::bytea, decode('000000000000000000000000', 'hex'), 1, 'abcd', now(), now(), now())")
                .update();
        db.sql(
                        "INSERT INTO guide_request (security_id, account_id, usage_date, slot, status, provider, model,"
                                + " account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot, return_rate_snapshot,"
                                + " currency, raw_text, requested_at, completed_at)"
                                + " VALUES (1, :a, current_date, 1, 'SUCCEEDED', 'OPENAI', 'gpt-5', '키움', 1, 1, 1, 0, 'KRW', '원문', now(), now())")
                .param("a", account)
                .update();

        migrate(ds, "9");

        Map<String, Object> legacy = db.sql("SELECT id, kakao_id, legacy, selected_ai_provider FROM app_user")
                .query()
                .singleRow();
        assertThat(legacy).containsEntry("legacy", true).containsEntry("selected_ai_provider", "OPENAI");
        assertThat(legacy.get("kakao_id")).isNull();
        Object legacyId = legacy.get("id");
        for (String table : new String[] {"account", "ai_credential", "guide_request"}) {
            assertThat(db.sql("SELECT DISTINCT user_id FROM " + table)
                            .query(Long.class)
                            .list())
                    .as(table)
                    .containsExactly((Long) legacyId);
        }
        assertThat(db.sql("SELECT to_regclass('owner') IS NULL")
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void noOwnerMeansNoLegacyUser() {
        DriverManagerDataSource ds = freshDatabase();
        migrate(ds, "9");
        assertThat(JdbcClient.create(ds)
                        .sql("SELECT count(*) FROM app_user")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
