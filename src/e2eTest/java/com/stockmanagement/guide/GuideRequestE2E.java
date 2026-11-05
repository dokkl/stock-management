package com.stockmanagement.guide;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** quickstart S5(US3) 1~4단계와 S4 1단계(키 없으면 설정 화면으로 이동). */
class GuideRequestE2E extends E2ETestBase {

    private static final String GUIDE_JSON = "{\\\"summary\\\":\\\"최근 60일 동안 완만하게 올랐습니다.\\\","
            + "\\\"diagnosis\\\":\\\"평단가보다 0.7% 낮습니다.\\\",\\\"action\\\":\\\"HOLD\\\","
            + "\\\"rationale\\\":\\\"이동평균 위에서 움직이고 있습니다.\\\",\\\"risks\\\":[\\\"반도체 업황 변동\\\",\\\"환율\\\"]}";

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private CredentialCipher cipher;

    @BeforeEach
    void seed() {
        Fixtures f = new Fixtures(jdbc);
        long samsung = f.samsung();
        f.holding(f.account("키움 일반"), samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "OPEN", Instant.now());

        WIREMOCK.resetAll();
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .willReturn(json("{\"result\":{\"candles\":[{\"timestamp\":\"2026-11-04T00:00:00+09:00\","
                        + "\"closePrice\":\"70000\"}],\"nextBefore\":null}}")));
        WIREMOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(json("{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,"
                                + "\"model\":\"gpt-5-2026-08-01\",\"choices\":[{\"index\":0,\"message\":"
                                + "{\"role\":\"assistant\",\"content\":\"" + GUIDE_JSON
                                + "\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")
                        .withFixedDelay(1_000)));
    }

    private Locator guideButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("삼성전자 가이드 요청"));
    }

    private Locator panel() {
        return page.locator("#guide-panel");
    }

    @Test
    void withoutKeyGuideButtonLeadsToSettings() {
        setupAndLogin();

        guideButton().click();

        page.waitForURL(url("/settings/ai"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("AI 설정")))
                .isVisible();
    }

    @Test
    void requestsGuideTwiceThenButtonIsDisabled() {
        setupAndLogin();
        credentialStore.save(AiProvider.OPENAI, cipher.encrypt(ApiKey.of("sk-proj-e2e-guide-key-1234")), Instant.now());
        credentialStore.select(AiProvider.OPENAI);
        page.reload();
        waitForHtmx();
        assertThat(guideButton()).hasText("가이드 (남은 2회)");

        // S5-1: 분석 진행 카드(단계·진행 막대) 후 결과(요약·진단·행동·근거·위험·면책·공급자와 모델)
        guideButton().click();
        assertThat(panel().locator("[aria-busy=true]")).isVisible();
        assertThat(panel().locator(".analysis-steps li.is-active")).containsText("시세·일봉 데이터 수집");
        assertThat(panel().getByRole(AriaRole.PROGRESSBAR)).isVisible();
        assertNoAccessibilityViolations();
        assertThat(panel())
                .containsText(
                        "최근 60일 동안 완만하게 올랐습니다.",
                        new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions()
                                .setTimeout(15_000));
        assertThat(panel()).containsText("평단가보다 0.7% 낮습니다.");
        assertThat(panel()).containsText("보유");
        assertThat(panel()).containsText("이동평균 위에서");
        assertThat(panel()).containsText("반도체 업황 변동");
        assertThat(panel()).containsText("투자 자문이 아닙니다");
        assertThat(panel()).containsText("OpenAI · gpt-5-2026-08-01");
        assertThat(guideButton()).hasText("가이드 (남은 1회)");
        assertNoAccessibilityViolations();

        // S5-2: 두 번째 요청 후 남은 횟수 0, 비활성, 초기화 안내
        guideButton().click();
        assertThat(panel().locator("[aria-busy=true]")).isVisible();
        assertThat(panel().locator("article"))
                .isVisible(new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                        .setTimeout(15_000));
        assertThat(guideButton()).hasText("가이드 (남은 0회)");
        assertThat(guideButton()).isDisabled();
        assertThat(page.getByText("내일 00:00 (KST) 초기화")).isVisible();
        assertNoAccessibilityViolations();
    }

    @Test
    void claudeGuideShowsSupplyDemandAndSourcedIssues() {
        setupAndLogin();
        credentialStore.save(
                AiProvider.ANTHROPIC, cipher.encrypt(ApiKey.of("sk-ant-e2e-guide-key-5678")), Instant.now());
        credentialStore.select(AiProvider.ANTHROPIC);
        String guide = "{\\\"summary\\\":\\\"완만한 상승 흐름입니다.\\\",\\\"diagnosis\\\":\\\"평단가 근처입니다.\\\","
                + "\\\"supplyDemand\\\":\\\"외국인이 순매수 중입니다.\\\",\\\"issues\\\":[{\\\"title\\\":\\\"반도체 수출 규제 강화\\\","
                + "\\\"summary\\\":\\\"대중 수출 규제가 강화되었습니다.\\\",\\\"impact\\\":\\\"NEGATIVE\\\","
                + "\\\"sourceUrl\\\":\\\"https://news.example.com/chip\\\",\\\"sourceTitle\\\":\\\"예시 뉴스\\\","
                + "\\\"publishedDate\\\":\\\"2026-11-04\\\"}],\\\"overall\\\":\\\"수급은 좋지만 규제 이슈로 관망합니다.\\\","
                + "\\\"action\\\":\\\"WATCH\\\",\\\"rationale\\\":\\\"이슈를 확인한 뒤 판단합니다.\\\",\\\"risks\\\":[\\\"규제\\\"]}";
        WIREMOCK.stubFor(post(urlPathEqualTo("/v1/messages"))
                .willReturn(json("{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                        + "\"model\":\"claude-sonnet-5-5-20261001\",\"content\":["
                        + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\",\"input\":{\"query\":\"삼성전자\"}},"
                        + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_1\",\"content\":["
                        + "{\"type\":\"web_search_result\",\"url\":\"https://news.example.com/chip\",\"title\":\"예시 뉴스\","
                        + "\"encrypted_content\":\"enc\",\"page_age\":\"2026-11-04\"}]},"
                        + "{\"type\":\"text\",\"text\":\"" + guide + "\"}],"
                        + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
                        + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}")));
        page.reload();
        waitForHtmx();

        guideButton().click();

        // 결론만 먼저 보이고, 나머지는 "자세히 보기"를 눌러야 펼쳐진다
        assertThat(panel().locator(".guide-headline"))
                .hasText(
                        "수급은 좋지만 규제 이슈로 관망합니다.",
                        new com.microsoft.playwright.assertions.LocatorAssertions.HasTextOptions().setTimeout(15_000));
        assertThat(panel().getByText("외국인이 순매수 중입니다.")).isHidden();
        panel().getByText("자세히 보기").click();
        assertThat(panel().getByText("외국인이 순매수 중입니다.")).isVisible();
        assertThat(panel()).containsText("악재");
        Locator source = panel().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("예시 뉴스"));
        assertThat(source).hasAttribute("href", "https://news.example.com/chip");
        assertThat(source).hasAttribute("target", "_blank");
        assertThat(panel()).containsText("Claude · claude-sonnet-5-5-20261001");
        assertNoAccessibilityViolations();
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
