package com.stockmanagement.guide;

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
                .andExpect(
                        content().string(allOf(containsString("hx-trigger=\"every 2s\""), containsString("/status"))));
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
    void pendingStatusKeepsPolling() throws Exception {
        ai.openAiGuide(AiStubs.GUIDE_JSON, "gpt-5", 1_500);
        requestGuide(g.holding).andExpect(status().isAccepted());

        pollStatus(g.latestGuideId())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("every 2s")));
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
