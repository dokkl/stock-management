package com.stockmanagement.guide;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import com.stockmanagement.guide.domain.GuideAction;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;
import com.stockmanagement.guide.domain.GuidePromptBuilder;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 가이드 생성 어댑터(T097): 구조화 성공, 원문 폴백, 키 오류·일시 장애 분류, 응답 모델명. */
class AiGenerateGuideIT extends IntegrationTestBase {

    static final String GUIDE_JSON = """
            {"summary":"최근 60일 동안 완만하게 올랐습니다.","diagnosis":"평단가보다 0.7% 낮습니다.",\
            "action":"HOLD","rationale":"이동평균 위에서 움직이고 있습니다.","risks":["반도체 업황 변동","환율"]}""";

    private static final ApiKey KEY = ApiKey.of("sk-test-generate-key-0001");
    private static final GuidePrompt PROMPT = GuidePromptBuilder.build(
            new GuidePromptBuilder.Stock(Market.KR, "005930", "삼성전자", Currency.KRW),
            new BigDecimal("71000"),
            new BigDecimal("70000"),
            List.of(new BigDecimal("71000"), new BigDecimal("70000"), new BigDecimal("69500")),
            new BigDecimal("71500"),
            BigDecimal.TEN);

    @Autowired
    private List<AiGuideProvider> providers;

    private GuideOutcome anthropic() {
        return provider(AiProvider.ANTHROPIC).generateGuide(KEY, PROMPT);
    }

    private GuideOutcome openAi() {
        return provider(AiProvider.OPENAI).generateGuide(KEY, PROMPT);
    }

    private AiGuideProvider provider(AiProvider type) {
        return providers.stream().filter(p -> p.provider() == type).findFirst().orElseThrow();
    }

    @Test
    void anthropicJsonMatchingSchemaIsStructured() {
        stubAnthropicText(GUIDE_JSON, "claude-sonnet-5-5-20261001");

        GuideOutcome outcome = anthropic();

        assertThat(outcome).isInstanceOfSatisfying(GuideOutcome.Generated.class, g -> {
            assertThat(g.model()).isEqualTo("claude-sonnet-5-5-20261001");
            assertThat(g.content().action()).isEqualTo(GuideAction.HOLD);
            assertThat(g.content().risks()).containsExactly("반도체 업황 변동", "환율");
            assertThat(g.rawText()).isNull();
        });
    }

    @Test
    void openAiJsonIsStructuredWithResponseModel() {
        stubOpenAiText(GUIDE_JSON, "gpt-5-2026-08-01");

        assertThat(openAi()).isInstanceOfSatisfying(GuideOutcome.Generated.class, g -> {
            assertThat(g.model()).isEqualTo("gpt-5-2026-08-01");
            assertThat(g.content().summary()).startsWith("최근 60일");
        });
    }

    @Test
    void claudeSearchesWebAndKeepsOnlyIssuesFromSearchResults() {
        new AiStubs(WIREMOCK)
                .anthropicGuideWithSearch(
                        AiStubs.comprehensiveJson("https://news.example.com/chip/", "https://made-up.example.com/fake"),
                        "https://news.example.com/chip",
                        "https://news.example.com/fx");

        GuideOutcome outcome = anthropic();

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.ANTHROPIC_PATH))
                .withRequestBody(containing("web_search"))
                .withRequestBody(containing("웹 검색으로")));
        assertThat(outcome).isInstanceOfSatisfying(GuideOutcome.Generated.class, g -> {
            assertThat(g.model()).isEqualTo("claude-sonnet-5-5-20261001");
            assertThat(g.content().isComprehensive()).isTrue();
            assertThat(g.content().supplyDemand()).isEqualTo("외국인이 5일 연속 순매수했습니다.");
            assertThat(g.content().issues())
                    .as("검색 결과에 없는 출처는 버린다")
                    .extracting(issue -> issue.sourceUrl())
                    .containsExactly("https://news.example.com/chip/");
        });
    }

    @Test
    void openAiDoesNotSearchAndDropsIssues() {
        stubOpenAiText(AiStubs.comprehensiveJson("https://news.example.com/chip"), "gpt-5");

        GuideOutcome outcome = openAi();

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.OPENAI_PATH))
                .withRequestBody(notContaining("web_search"))
                .withRequestBody(containing("빈 배열")));
        assertThat(outcome).isInstanceOfSatisfying(GuideOutcome.Generated.class, g -> {
            assertThat(g.content().issues()).isEmpty();
            assertThat(g.content().overall()).contains("수출 규제");
        });
    }

    @Test
    void jsonAfterExplanationIsStillStructured() {
        stubAnthropicText("분석 결과입니다.\n" + GUIDE_JSON + "\n참고하세요.", "claude-sonnet-5-5");

        assertThat(anthropic())
                .isInstanceOfSatisfying(
                        GuideOutcome.Generated.class,
                        g -> assertThat(g.content()).isNotNull());
    }

    @Test
    void promptCarriesSupplyDemandSection() {
        stubOpenAiText(GUIDE_JSON, "gpt-5");

        openAi();

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.OPENAI_PATH))
                .withRequestBody(containing("[수급]"))
                .withRequestBody(containing("supplyDemand")));
    }

    @Test
    void claudeResponseCutAtMaxTokensIsTruncated() {
        String partial = GUIDE_JSON.substring(0, GUIDE_JSON.indexOf("\"rationale\"") + 20);
        WIREMOCK.stubFor(post(urlPathEqualTo(AiStubs.ANTHROPIC_PATH))
                .willReturn(json(
                        200,
                        "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                                + "\"model\":\"claude-sonnet-5-5\",\"content\":[{\"type\":\"text\",\"text\":"
                                + quote(partial)
                                + "}],\"stop_reason\":\"max_tokens\",\"stop_sequence\":null,"
                                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":16000}}")));

        assertThat(anthropic()).isEqualTo(new GuideOutcome.Truncated());
        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.ANTHROPIC_PATH))
                .withRequestBody(containing("\"max_tokens\":16000")));
    }

    @Test
    void openAiResponseCutAtLengthIsTruncated() {
        new AiStubs(WIREMOCK).openAiTruncated(GUIDE_JSON.substring(0, 80));

        assertThat(openAi()).isEqualTo(new GuideOutcome.Truncated());
    }

    @Test
    void plainTextEndingNormallyStaysRawText() {
        stubAnthropicText("지금은 관망이 좋겠습니다.", "claude-sonnet-5-5");

        assertThat(anthropic()).isInstanceOf(GuideOutcome.Generated.class);
    }

    @Test
    void schemaMismatchFallsBackToRawText() {
        stubAnthropicText("분석: 지금은 관망이 좋겠습니다.", "claude-sonnet-5-5");

        assertThat(anthropic()).isInstanceOfSatisfying(GuideOutcome.Generated.class, g -> {
            assertThat(g.content()).isNull();
            assertThat(g.rawText()).isEqualTo("분석: 지금은 관망이 좋겠습니다.");
        });
    }

    @Test
    void outOfRangeFieldsFallBackToRawText() {
        stubAnthropicText(GUIDE_JSON.replace("[\"반도체 업황 변동\",\"환율\"]", "[]"), "claude-sonnet-5-5");

        assertThat(anthropic())
                .isInstanceOfSatisfying(
                        GuideOutcome.Generated.class,
                        g -> assertThat(g.content()).isNull());
    }

    @Test
    void authenticationAndBalanceErrorsAreCredentialErrors() {
        stubAnthropicError(401, "authentication_error", "invalid x-api-key");
        assertThat(anthropic()).isEqualTo(new GuideOutcome.CredentialError(401));

        stubAnthropicError(402, "billing_error", "payment required");
        assertThat(anthropic()).isEqualTo(new GuideOutcome.CredentialError(402));

        stubAnthropicError(400, "invalid_request_error", "Your credit balance is too low to access the API");
        assertThat(anthropic()).isEqualTo(new GuideOutcome.CredentialError(400));

        stubOpenAiError(429, "insufficient_quota", "You exceeded your current quota");
        assertThat(openAi()).isEqualTo(new GuideOutcome.CredentialError(429));
    }

    @Test
    void rateLimitServerErrorAndTimeoutAreTransient() {
        stubOpenAiError(429, "rate_limit_exceeded", "Rate limit reached");
        assertThat(openAi()).isEqualTo(new GuideOutcome.TransientError(429));

        stubAnthropicError(529, "overloaded_error", "Overloaded");
        assertThat(anthropic()).isEqualTo(new GuideOutcome.TransientError(529));

        WIREMOCK.stubFor(post(urlPathEqualTo(AiStubs.ANTHROPIC_PATH))
                .willReturn(anthropicText(GUIDE_JSON, "claude-sonnet-5-5").withFixedDelay(3_000)));
        assertThat(anthropic()).isEqualTo(new GuideOutcome.TransientError(0));
    }

    static void stubAnthropicText(String text, String model) {
        WIREMOCK.stubFor(post(urlPathEqualTo(AiStubs.ANTHROPIC_PATH)).willReturn(anthropicText(text, model)));
    }

    static ResponseDefinitionBuilder anthropicText(String text, String model) {
        return json(
                200,
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"" + model + "\","
                        + "\"content\":[{\"type\":\"text\",\"text\":" + quote(text) + "}],\"stop_reason\":\"end_turn\","
                        + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":10}}");
    }

    static void stubOpenAiText(String text, String model) {
        WIREMOCK.stubFor(post(urlPathEqualTo(AiStubs.OPENAI_PATH))
                .willReturn(json(
                        200,
                        "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,"
                                + "\"model\":\"" + model
                                + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                                + "\"content\":" + quote(text) + "},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":10,\"total_tokens\":20}}")));
    }

    private static void stubAnthropicError(int status, String type, String message) {
        WIREMOCK.stubFor(post(urlPathEqualTo(AiStubs.ANTHROPIC_PATH))
                .willReturn(json(
                        status,
                        "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}")));
    }

    private static void stubOpenAiError(int status, String code, String message) {
        WIREMOCK.stubFor(post(urlPathEqualTo(AiStubs.OPENAI_PATH))
                .willReturn(json(
                        status,
                        "{\"error\":{\"message\":\"" + message + "\",\"type\":\"" + code + "\",\"code\":\"" + code
                                + "\"}}")));
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
