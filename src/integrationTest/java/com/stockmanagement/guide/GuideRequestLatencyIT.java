package com.stockmanagement.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.GuideFixture;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 헌법 원칙 IV·research R8(T100): AI가 10초 걸려도 가이드 요청 응답은 500ms 안에 온다. */
class GuideRequestLatencyIT extends IntegrationTestBase {

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private CredentialCipher cipher;

    private GuideFixture g;

    @AfterEach
    void waitForBackgroundWork() {
        g.awaitNoPending(Duration.ofSeconds(15));
    }

    @Test
    void requestReturnsWithinBudgetWhileAiIsSlow() throws Exception {
        owner();
        g = new GuideFixture(jdbc).samsungHolding(clock.instant());
        long apple = g.fixtures().apple();
        long warmUpHolding = g.fixtures().holding(g.account, apple, "185.2", "1");
        g.fixtures().quote(apple, "187.50", "190.00", "OPEN", clock.instant());
        createOwner();
        GuideFixture.storeOpenAiKey(credentialStore, cipher, clock.instant());
        GuideFixture.stubMarket(WIREMOCK);
        new AiStubs(WIREMOCK).openAiGuide(AiStubs.GUIDE_JSON, "gpt-5", 10_000);
        post(warmUpHolding); // JIT·템플릿 캐시 예열

        long start = System.nanoTime();
        post(g.holding);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
    }

    private void post(long holdingId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/holdings/" + holdingId + "/guides")
                        .with(owner())
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isAccepted());
    }
}
