package com.stockmanagement.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.UserId;
import com.stockmanagement.user.web.AppUserPrincipal;
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

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    protected static final WireMockServer WIREMOCK =
            new WireMockServer(wireMockConfig().dynamicPort().globalTemplating(true));

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.toss.base-url", WIREMOCK::baseUrl);
        registry.add("app.kakao.auth-base-url", WIREMOCK::baseUrl);
        registry.add("app.kakao.api-base-url", WIREMOCK::baseUrl);
        registry.add("app.kakao.admin-key", () -> KakaoStubs.ADMIN_KEY);
        registry.add("spring.security.oauth2.client.registration.kakao.client-id", () -> "test-kakao-client");
        registry.add("spring.security.oauth2.client.registration.kakao.client-secret", () -> "test-kakao-secret");
        registry.add("app.ai.base-urls.anthropic", WIREMOCK::baseUrl);
        registry.add("app.ai.base-urls.openai", () -> WIREMOCK.baseUrl() + "/v1");
        registry.add("app.ai.timeout", () -> "2s");
        registry.add("app.ai.guide-timeout", () -> "2s");
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

    /** 기본 사용자(카카오 1001)를 만든다. */
    protected UserId createOwner() {
        return new UserId(new Fixtures(jdbc).defaultUser());
    }

    /** 기본 사용자로 로그인한 요청. 사용자 행도 함께 만든다. */
    protected RequestPostProcessor owner() {
        return as(createOwner());
    }

    /** 주어진 사용자로 로그인한 요청(카카오 로그인을 마친 세션과 같다). */
    protected RequestPostProcessor as(UserId userId) {
        return oauth2Login().oauth2User(new AppUserPrincipal(userId, DisplayName.of("테스트 사용자")));
    }

    /** 카카오 사용자를 만들고 그 UserId를 돌려준다. */
    protected UserId userWithKakao(long kakaoId) {
        return new UserId(new Fixtures(jdbc).user(kakaoId, "사용자" + kakaoId));
    }
}
