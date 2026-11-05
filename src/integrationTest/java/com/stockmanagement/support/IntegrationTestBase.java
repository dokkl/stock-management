package com.stockmanagement.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stockmanagement.auth.application.SetupService;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공통 기반: PostgreSQL 18 Testcontainer(싱글턴), WireMock(외부 API 대역), 테스트 후 테이블 초기화.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class IntegrationTestBase {

    public static final String OWNER_PASSWORD = "correct-horse-battery";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    protected static final WireMockServer WIREMOCK =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.toss.base-url", WIREMOCK::baseUrl);
        registry.add("app.ai.base-urls.anthropic", WIREMOCK::baseUrl);
        registry.add("app.ai.base-urls.openai", () -> WIREMOCK.baseUrl() + "/v1");
        registry.add("app.ai.timeout", () -> "2s");
        registry.add("app.toss.client-id", () -> "test-client-id");
        registry.add("app.toss.client-secret", () -> "test-client-secret");
        registry.add("app.crypto.key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.scheduling.enabled", () -> "false");
        registry.add("app.market.master-sync-on-startup", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private SetupService setupService;

    @BeforeEach
    void resetExternalState() {
        WIREMOCK.resetAll();
        clock.set(Instant.parse(TestClockConfig.DEFAULT_INSTANT));
    }

    @AfterEach
    void truncateTables() {
        List<String> tables = jdbc.sql(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'")
                .query(String.class)
                .list();
        if (!tables.isEmpty()) {
            jdbc.sql("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE")
                    .update();
        }
    }

    protected void createOwner() {
        setupService.setup(OWNER_PASSWORD, OWNER_PASSWORD);
    }

    /** 소유자로 인증된 요청. 소유자 행도 함께 만든다. */
    protected RequestPostProcessor owner() {
        if (jdbc.sql("SELECT count(*) FROM owner").query(Long.class).single() == 0) {
            createOwner();
        }
        return user("owner").roles("OWNER");
    }
}
