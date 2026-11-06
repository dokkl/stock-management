package com.stockmanagement.aicredential;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.support.E2ETestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * quickstart S4: AI 키 등록과 선택 (US4). 관리자(기본 사용자) 전용이며 등록한 키는 모든 사용자가 쓴다. 가이드 버튼에서 설정 화면으로
 * 이동하는 1단계는 US3에서 다룬다.
 */
class AiSettingsE2E extends E2ETestBase {

    private static final String CLAUDE_KEY = "sk-ant-api03-e2e-valid-abcd";
    private static final String OPENAI_KEY = "sk-proj-e2e-valid-key-9876";

    @BeforeEach
    void stubProviders() {
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(post(urlPathEqualTo("/v1/messages")).atPriority(10).willReturn(error(401)));
        WIREMOCK.stubFor(
                post(urlPathEqualTo("/v1/chat/completions")).atPriority(10).willReturn(error(401)));
        WIREMOCK.stubFor(post(urlPathEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo(CLAUDE_KEY))
                .willReturn(ok("""
                        {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                         "content":[{"type":"text","text":"pong"}],"stop_reason":"max_tokens","stop_sequence":null,
                         "usage":{"input_tokens":1,"output_tokens":1}}""")));
        WIREMOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer " + OPENAI_KEY))
                .willReturn(ok("""
                        {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"gpt-5",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"length"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}""")));
    }

    private Locator card(String provider) {
        return page.locator(".provider-card").filter(new Locator.FilterOptions().setHasText(provider + " API 키"));
    }

    private void submitKey(String provider, String key) {
        Locator card = card(provider);
        card.getByLabel(provider + " API 키").fill(key);
        page.waitForResponse(
                r -> r.url().contains("/settings/ai/credentials/"),
                () -> card.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("키 저장"))
                        .click());
        waitForHtmxIdle();
    }

    @Test
    void registersVerifiesAndSelectsProviders() {
        setupAndLogin();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("AI 설정"))
                .click();
        page.waitForURL(url("/settings/ai"));
        waitForHtmx();
        assertThat(card("Claude")).containsText("키 필요");
        assertNoAccessibilityViolations();

        // S4-2: 잘못된 키는 거부되고 입력란은 비워진다
        submitKey("Claude", "sk-ant-api03-e2e-wrong-0000");
        assertThat(card("Claude")).containsText("키를 확인할 수 없습니다");
        assertThat(card("Claude").getByLabel("Claude API 키")).hasValue("");
        assertNoAccessibilityViolations();

        // S4-3: 올바른 키는 끝 4자리만 보이고 자동 선택된다
        submitKey("Claude", CLAUDE_KEY);
        assertThat(card("Claude")).containsText("****abcd");
        assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("Claude")))
                .isChecked();
        assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("OpenAI (키 필요)")))
                .isDisabled();

        // S4-4: OpenAI 키도 등록하고 OpenAI를 선택한다
        submitKey("OpenAI", OPENAI_KEY);
        assertThat(card("OpenAI")).containsText("****9876");
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("OpenAI"))
                .check();
        page.waitForResponse(
                r -> r.url().endsWith("/settings/ai/selection"),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("선택 저장"))
                        .click());
        waitForHtmxIdle();
        assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("OpenAI")))
                .isChecked();

        page.reload();
        assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("OpenAI")))
                .isChecked();
        org.assertj.core.api.Assertions.assertThat(page.content()).doesNotContain(CLAUDE_KEY);
        assertNoAccessibilityViolations();
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder ok(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder error(int status) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid\"}}");
    }

    @Test
    void memberHasNoAiSettingsMenuOrPage() {
        loginAs(2002, "일반 사용자");
        assertThat(page.getByRole(AriaRole.NAVIGATION)
                        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("AI 설정")))
                .hasCount(0);

        page.navigate(url("/settings/ai"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("페이지를 찾을 수 없습니다");
        assertNoAccessibilityViolations();
    }
}
