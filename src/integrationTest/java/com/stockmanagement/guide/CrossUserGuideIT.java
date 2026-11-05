package com.stockmanagement.guide;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.GuideFixture;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.user.domain.UserId;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 가이드 사용자 격리(T028): 남의 가이드·진행 상태·보유 종목 가이드 요청은 404, 기록은 자기 것만, 키도 자기 것만. */
class CrossUserGuideIT extends IntegrationTestBase {

    private static final String A_KEY = "sk-proj-user-a-openai-1111";
    private static final String B_KEY = "sk-proj-user-b-openai-2222";

    @Autowired
    private CredentialStore credentials;

    @Autowired
    private CredentialCipher cipher;

    private UserId a;
    private UserId b;
    private long samsung;
    private long aHolding;
    private long bHolding;
    private GuideFixture g;

    @BeforeEach
    void setUp() {
        Fixtures f = new Fixtures(jdbc);
        a = userWithKakao(7001);
        b = userWithKakao(7002);
        samsung = f.samsung();
        aHolding = f.holding(f.account(a.value(), "A 계좌"), samsung, "71500", "10");
        bHolding = f.holding(f.account(b.value(), "B 계좌"), samsung, "70000", "1");
        f.quote(samsung, "71000", "70000", "OPEN", clock.instant());
        credentials.save(a, AiProvider.OPENAI, cipher.encrypt(ApiKey.of(A_KEY)), clock.instant());
        credentials.select(a, AiProvider.OPENAI);
        credentials.save(b, AiProvider.OPENAI, cipher.encrypt(ApiKey.of(B_KEY)), clock.instant());
        credentials.select(b, AiProvider.OPENAI);
        GuideFixture.stubMarket(WIREMOCK);
        new AiStubs(WIREMOCK).openAiGuide(AiStubs.GUIDE_JSON, "gpt-5", 0);
        g = new GuideFixture(jdbc);
    }

    @AfterEach
    void waitForBackgroundWork() {
        g.awaitNoPending(Duration.ofSeconds(10));
    }

    private long requestGuide(UserId user, long holding) throws Exception {
        mockMvc.perform(post("/holdings/" + holding + "/guides")
                        .with(as(user))
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isAccepted());
        long id = g.latestGuideId();
        g.awaitTerminal(id, Duration.ofSeconds(10));
        return id;
    }

    @Test
    void foreignGuideRoutesAre404() throws Exception {
        long aGuide = requestGuide(a, aHolding);

        mockMvc.perform(get("/guides/" + aGuide).with(as(b))).andExpect(status().isNotFound());
        mockMvc.perform(get("/guides/" + aGuide + "/status").with(as(b)).header("HX-Request", "true"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/holdings/" + aHolding + "/guides")
                        .with(as(b))
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/holdings/" + aHolding + "/guide-button").with(as(b)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT count(*) FROM guide_request")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void historyShowsOnlyOwnRecordsForSharedSecurity() throws Exception {
        requestGuide(a, aHolding);

        mockMvc.perform(get("/securities/" + samsung + "/guides").with(as(b)))
                .andExpect(content().string(allOf(containsString("아직 받은 가이드가 없습니다"), not(containsString("A 계좌")))));
        mockMvc.perform(get("/securities/" + samsung + "/guides").with(as(a)))
                .andExpect(content().string(containsString("A 계좌")));
    }

    @Test
    void guideUsesRequestersOwnKey() throws Exception {
        requestGuide(b, bHolding);

        com.stockmanagement.support.IntegrationTestBase.WIREMOCK.verify(
                postRequestedFor(urlPathEqualTo(AiStubs.OPENAI_PATH))
                        .withHeader("Authorization", equalTo("Bearer " + B_KEY)));
        com.stockmanagement.support.IntegrationTestBase.WIREMOCK.verify(
                0,
                postRequestedFor(urlPathEqualTo(AiStubs.OPENAI_PATH))
                        .withHeader("Authorization", equalTo("Bearer " + A_KEY)));
    }
}
