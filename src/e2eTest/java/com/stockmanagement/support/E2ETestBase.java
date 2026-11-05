package com.stockmanagement.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** E2E 공통 기반: 실제 서버(RANDOM_PORT) + Chromium + axe 접근성 검사(헌법 원칙 III). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2ETestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    protected static final WireMockServer WIREMOCK =
            new WireMockServer(wireMockConfig().dynamicPort().globalTemplating(true));

    private static Playwright playwright;
    protected static Browser browser;

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
        registry.add("app.crypto.key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.scheduling.enabled", () -> "false");
        registry.add("app.market.master-sync-on-startup", () -> "false");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbc;

    protected BrowserContext context;
    private final java.util.List<BrowserContext> extraContexts = new java.util.ArrayList<>();
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setLocale("ko-KR"));
        page = context.newPage();
    }

    @AfterEach
    void cleanUp() {
        context.close();
        extraContexts.forEach(BrowserContext::close);
        extraContexts.clear();
        List<String> tables = jdbc.sql(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'")
                .query(String.class)
                .list();
        jdbc.sql("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE")
                .update();
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 기본 사용자(카카오 1001)의 UserId. 사용자 행이 없으면 만든다. */
    protected com.stockmanagement.user.domain.UserId defaultUserId() {
        return new com.stockmanagement.user.domain.UserId(new Fixtures(jdbc).defaultUser());
    }

    /** 기본 사용자(카카오 1001, Fixtures로 만든 데이터의 주인)로 카카오 로그인한다. */
    protected void setupAndLogin() {
        loginAs(Fixtures.DEFAULT_KAKAO_ID, Fixtures.DEFAULT_NICKNAME);
    }

    /** WireMock 카카오 대역으로 실제 로그인 흐름(버튼 → 인가 → 콜백)을 거친다. */
    protected void loginAs(long kakaoId, String nickname) {
        loginAs(page, kakaoId, nickname);
    }

    /** 주어진 페이지(다른 브라우저 컨텍스트 가능)에서 카카오 로그인한다. 인가 대역은 마지막에 등록한 사용자로 응답한다. */
    protected void loginAs(Page target, long kakaoId, String nickname) {
        new KakaoStubs(WIREMOCK).user(kakaoId, nickname).authorizeAs(kakaoId);
        target.navigate(url("/login"));
        target.getByRole(
                        com.microsoft.playwright.options.AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("카카오로 시작하기"))
                .click();
        target.waitForURL(url("/"));
        target.waitForFunction("() => window.htmx !== undefined");
    }

    /** 다른 사용자를 위한 별도 브라우저 컨텍스트(쿠키 분리). 테스트가 끝나면 닫힌다. */
    protected Page openSecondBrowser() {
        BrowserContext second = browser.newContext(new Browser.NewContextOptions().setLocale("ko-KR"));
        extraContexts.add(second);
        return second.newPage();
    }

    /** htmx 요청·스왑·정착이 모두 끝날 때까지 기다린다(새로 붙은 요소의 트리거가 연결된 뒤에 조작하기 위해). */
    protected void waitForHtmxIdle() {
        page.waitForFunction(
                "() => !document.querySelector('.htmx-request, .htmx-swapping, .htmx-settling, .htmx-added')");
    }

    /** htmx가 로드되기 전에 폼을 제출하면 일반 POST가 되므로 로드를 기다린다. */
    protected void waitForHtmx() {
        page.waitForFunction("() => window.htmx !== undefined");
    }

    /** 현재 화면의 WCAG 2.1 AA 위반이 0건인지 검사한다. */
    protected void assertNoAccessibilityViolations() {
        AxeResults results = new AxeBuilder(page)
                .withTags(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa"))
                .analyze();
        List<Rule> violations = results.getViolations();
        assertThat(violations)
                .as(() -> violations.stream()
                        .map(v -> v.getId() + ": " + v.getHelp())
                        .collect(Collectors.joining("\n")))
                .isEmpty();
    }
}
