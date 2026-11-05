package com.stockmanagement.guide;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.GuideFixture;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** contracts/web-routes.md AI 가이드 경로(T098). */
class GuideRoutesIT extends IntegrationTestBase {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private CredentialCipher cipher;

    private final AiStubs ai = new AiStubs(WIREMOCK);
    private GuideFixture g;

    @BeforeEach
    void setUp() {
        owner();
        g = new GuideFixture(jdbc).samsungHolding(clock.instant());
        GuideFixture.storeOpenAiKey(credentialStore, cipher, clock.instant());
        GuideFixture.stubMarket(WIREMOCK);
        ai.openAiGuide(AiStubs.GUIDE_JSON, "gpt-5-2026-08-01", 0);
    }

    @AfterEach
    void waitForBackgroundWork() {
        g.awaitNoPending(WAIT);
    }

    /** 기본 성공 응답 대신 오류를 돌려주게 한다(오류 스텁은 우선순위가 낮아 성공 스텁을 지워야 한다). */
    private void aiFailsWith(int status) {
        WIREMOCK.resetAll();
        GuideFixture.stubMarket(WIREMOCK);
        ai.openAiResponds(status);
    }

    private ResultActions requestGuide(long holdingId) throws Exception {
        return mockMvc.perform(post("/holdings/" + holdingId + "/guides")
                .with(owner())
                .with(csrf())
                .header("HX-Request", "true"));
    }

    private ResultActions pollStatus(long guideId) throws Exception {
        return mockMvc.perform(
                get("/guides/" + guideId + "/status").with(owner()).header("HX-Request", "true"));
    }

    private long activeCount() {
        return jdbc.sql("SELECT count(*) FROM guide_request WHERE status <> 'FAILED'")
                .query(Long.class)
                .single();
    }

    @Test
    void acceptedRequestPollsUntilResultWithProviderAndModel() throws Exception {
        requestGuide(g.holding)
                .andExpect(status().isAccepted())
                .andExpect(header().string("HX-Trigger", containsString("guide-quota-changed")))
                .andExpect(content()
                        .string(allOf(
                                containsString("hx-trigger=\"every 2s\""),
                                containsString("/status"),
                                containsString("data-guide-progress"),
                                containsString("시세·일봉 데이터 수집"),
                                containsString("data-expected=\"30\""),
                                not(containsString("국내외 이슈 검색")))));
        long id = g.latestGuideId();
        assertThat(g.awaitTerminal(id, WAIT)).isEqualTo("SUCCEEDED");

        pollStatus(id)
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", containsString("guide-quota-changed-" + g.samsung)))
                .andExpect(content()
                        .string(allOf(
                                containsString("최근 60일 동안 완만하게 올랐습니다."),
                                containsString("평단가보다 0.7% 낮습니다."),
                                containsString("보유"),
                                containsString("반도체 업황 변동"),
                                containsString("투자 자문이 아닙니다"),
                                containsString("OpenAI · gpt-5-2026-08-01"),
                                not(containsString("every 2s")))));
        assertThat(jdbc.sql("SELECT account_name_snapshot FROM guide_request")
                        .query(String.class)
                        .single())
                .isEqualTo("키움 일반");
    }

    @Test
    void koreanStockPromptIncludesSupplyDemandAndOpenAiShowsIssueNotice() throws Exception {
        ai.openAiGuide(AiStubs.comprehensiveJson("https://news.example.com/chip"), "gpt-5", 0);

        requestGuide(g.holding).andExpect(status().isAccepted());
        long id = g.latestGuideId();
        assertThat(g.awaitTerminal(id, WAIT)).isEqualTo("SUCCEEDED");

        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/stocks/005930/investor-trading")));
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/stocks/005930/short-selling")));
        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.OPENAI_PATH))
                .withRequestBody(containing("[수급]"))
                .withRequestBody(containing("외국인 1500 / 1500"))
                .withRequestBody(containing("50.89%")));
        pollStatus(id)
                .andExpect(content()
                        .string(allOf(
                                containsString("종합 판단"),
                                containsString("수출 규제 이슈가 부담입니다"),
                                containsString("외국인이 5일 연속 순매수했습니다."),
                                containsString("웹 검색을 지원하지 않아"),
                                not(containsString("news.example.com")))));
    }

    @Test
    void claudeGuideShowsVerifiedIssuesWithSourceLinks() throws Exception {
        credentialStore.save(
                com.stockmanagement.aicredential.domain.AiProvider.ANTHROPIC,
                cipher.encrypt(com.stockmanagement.aicredential.domain.ApiKey.of("sk-ant-guide-test-key-8888")),
                clock.instant());
        credentialStore.select(com.stockmanagement.aicredential.domain.AiProvider.ANTHROPIC);
        ai.anthropicGuideWithSearch(
                AiStubs.comprehensiveJson("https://news.example.com/chip", "https://made-up.example.com/x"),
                "https://news.example.com/chip");

        requestGuide(g.holding).andExpect(status().isAccepted());
        long id = g.latestGuideId();
        assertThat(g.awaitTerminal(id, WAIT)).isEqualTo("SUCCEEDED");

        pollStatus(id)
                .andExpect(content()
                        .string(allOf(
                                containsString("국내외 이슈"),
                                containsString("악재"),
                                containsString("이슈 1"),
                                containsString("href=\"https://news.example.com/chip\""),
                                containsString("rel=\"noopener noreferrer\""),
                                containsString("Claude · claude-sonnet-5-5-20261001"),
                                not(containsString("made-up.example.com")))));
    }

    @Test
    void supplyDataFailureDoesNotBlockGuide() throws Exception {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                        urlPathEqualTo("/api/v1/stocks/005930/investor-trading"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(500)));

        requestGuide(g.holding).andExpect(status().isAccepted());

        assertThat(g.awaitTerminal(g.latestGuideId(), WAIT)).isEqualTo("SUCCEEDED");
    }

    @Test
    void pendingStatusKeepsPolling() throws Exception {
        ai.openAiGuide(AiStubs.GUIDE_JSON, "gpt-5", 1_500);
        requestGuide(g.holding).andExpect(status().isAccepted());

        pollStatus(g.latestGuideId())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void repeatedClickWhilePendingReusesRequest() throws Exception {
        ai.openAiGuide(AiStubs.GUIDE_JSON, "gpt-5", 1_500);
        requestGuide(g.holding).andExpect(status().isAccepted());
        long first = g.latestGuideId();

        requestGuide(g.holding)
                .andExpect(status().isAccepted())
                .andExpect(content().string(containsString("guide-" + first)));

        assertThat(jdbc.sql("SELECT count(*) FROM guide_request")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void missingKeyRedirectsToSettings() throws Exception {
        jdbc.sql("DELETE FROM ai_credential").update();
        jdbc.sql("UPDATE owner SET selected_ai_provider = NULL").update();

        requestGuide(g.holding)
                .andExpect(status().isConflict())
                .andExpect(header().string("HX-Redirect", "/settings/ai"));
        assertThat(jdbc.sql("SELECT count(*) FROM guide_request")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void thirdRequestOfDayIs429WithResetTime() throws Exception {
        requestGuide(g.holding).andExpect(status().isAccepted());
        g.awaitTerminal(g.latestGuideId(), WAIT);
        requestGuide(g.holding).andExpect(status().isAccepted());
        g.awaitTerminal(g.latestGuideId(), WAIT);

        requestGuide(g.holding)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("내일 00:00 (KST)")));
        assertThat(activeCount()).isEqualTo(2);
    }

    @Test
    void suspendedOrDelistedSecurityIs409() throws Exception {
        jdbc.sql("UPDATE security SET trading_status = 'SUSPENDED'").update();
        requestGuide(g.holding)
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("거래정지")));

        jdbc.sql("UPDATE security SET trading_status = 'DELISTED'").update();
        requestGuide(g.holding)
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("상장폐지")));
        assertThat(jdbc.sql("SELECT count(*) FROM guide_request")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void missingHoldingIs404() throws Exception {
        requestGuide(9999).andExpect(status().isNotFound());
        mockMvc.perform(get("/guides/9999").with(owner())).andExpect(status().isNotFound());
    }

    @Test
    void transientFailureKeepsQuota() throws Exception {
        aiFailsWith(503);
        requestGuide(g.holding).andExpect(status().isAccepted());
        long id = g.latestGuideId();
        assertThat(g.awaitTerminal(id, WAIT)).isEqualTo("FAILED");

        pollStatus(id)
                .andExpect(content().string(allOf(containsString("일시적으로 응답하지 않습니다"), containsString("차감되지 않았습니다"))));
        assertThat(activeCount()).isZero();
        mockMvc.perform(get("/holdings/" + g.holding + "/guide-button").with(owner()))
                .andExpect(content().string(containsString("가이드 (남은 2회)")));
    }

    @Test
    void credentialFailureLinksToSettings() throws Exception {
        aiFailsWith(401);
        requestGuide(g.holding);
        long id = g.latestGuideId();
        g.awaitTerminal(id, WAIT);

        pollStatus(id)
                .andExpect(content()
                        .string(allOf(containsString("AI 키를 확인해 주세요"), containsString("href=\"/settings/ai\""))));
    }

    @Test
    void detailPageAndButtonShowState() throws Exception {
        requestGuide(g.holding);
        long id = g.latestGuideId();
        g.awaitTerminal(id, WAIT);

        mockMvc.perform(get("/guides/" + id).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("삼성전자 가이드")));
        mockMvc.perform(get("/holdings/" + g.holding + "/guide-button").with(owner()))
                .andExpect(content()
                        .string(allOf(
                                containsString("가이드 (남은 1회)"), containsString("guide-quota-changed-" + g.samsung))));
        mockMvc.perform(get("/").with(owner()))
                .andExpect(
                        content().string(allOf(containsString("id=\"guide-panel\""), containsString("가이드 (남은 1회)"))));
    }

    @Test
    void unauthenticatedHtmxIs401() throws Exception {
        mockMvc.perform(post("/holdings/" + g.holding + "/guides").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
    }
}
