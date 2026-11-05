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

    public static final String OWNER_PASSWORD = "correct-horse-battery";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    protected static final WireMockServer WIREMOCK =
            new WireMockServer(wireMockConfig().dynamicPort());

    private static Playwright playwright;
    private static Browser browser;

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.toss.base-url", WIREMOCK::baseUrl);
        registry.add("app.crypto.key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.scheduling.enabled", () -> "false");
        registry.add("app.market.master-sync-on-startup", () -> "false");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbc;

    protected BrowserContext context;
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

    protected void setupAndLogin() {
        page.navigate(url("/setup"));
        page.getByLabel("비밀번호", new Page.GetByLabelOptions().setExact(true)).fill(OWNER_PASSWORD);
        page.getByLabel("비밀번호 확인").fill(OWNER_PASSWORD);
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("설정 완료"))
                .click();
        page.getByLabel("비밀번호").fill(OWNER_PASSWORD);
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("로그인"))
                .click();
        page.waitForURL(url("/"));
        waitForHtmx();
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
