package com.stockmanagement.user;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.support.KakaoStubs;
import com.stockmanagement.user.application.UnlinkRetryScheduler;
import com.stockmanagement.user.application.UserService;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.KakaoId;
import com.stockmanagement.user.domain.UserId;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 회원 탈퇴(T037, FR-015·FR-016, SC-006): 데이터 연쇄 삭제, 세션 종료, 카카오 연결 해제와 재시도. */
class WithdrawalIT extends IntegrationTestBase {

    private static final long KAKAO_A = 13001;

    @Autowired
    private CredentialStore credentials;

    @Autowired
    private CredentialCipher cipher;

    @Autowired
    private UnlinkRetryScheduler retryScheduler;

    @Autowired
    private UserService userService;

    private final KakaoStubs kakao = new KakaoStubs(WIREMOCK);
    private UserId a;
    private UserId b;

    @BeforeEach
    void setUp() {
        Fixtures f = new Fixtures(jdbc);
        a = userWithKakao(KAKAO_A);
        b = userWithKakao(13002);
        long samsung = f.samsung();
        long aHolding = f.holding(f.account(a.value(), "A 계좌"), samsung, "71500", "10");
        f.holding(f.account(b.value(), "B 계좌"), samsung, "70000", "1");
        credentials.save(a, AiProvider.OPENAI, cipher.encrypt(ApiKey.of("sk-proj-withdraw-a-1111")), clock.instant());
        credentials.save(b, AiProvider.OPENAI, cipher.encrypt(ApiKey.of("sk-proj-withdraw-b-2222")), clock.instant());
        jdbc.sql("""
                        INSERT INTO guide_request (user_id, security_id, holding_id, account_id, usage_date, slot, status,
                            provider, model, account_name_snapshot, price_snapshot, avg_price_snapshot, quantity_snapshot,
                            return_rate_snapshot, currency, raw_text, requested_at, completed_at)
                        SELECT :u, :s, h.id, h.account_id, current_date, 1, 'SUCCEEDED', 'OPENAI', 'gpt-5', 'A 계좌',
                            1, 1, 1, 0, 'KRW', '원문', now(), now()
                        FROM holding h WHERE h.id = :h
                        """)
                .param("u", a.value())
                .param("s", samsung)
                .param("h", aHolding)
                .update();
    }

    private long count(String table, UserId user) {
        String where = table.equals("holding")
                ? "account_id IN (SELECT id FROM account WHERE user_id = :u)"
                : table.equals("app_user") ? "id = :u" : "user_id = :u";
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + where)
                .param("u", user.value())
                .query(Long.class)
                .single();
    }

    @Test
    void withdrawalDeletesOnlyOwnDataUnlinksAndEndsSession() throws Exception {
        kakao.unlinkOk();
        long holdingsBefore = count("holding", a);
        assertThat(holdingsBefore).isEqualTo(1);

        mockMvc.perform(post("/settings/account/withdraw").with(as(a)).with(csrf()))
                .andExpect(redirectedUrl("/login?withdrawn"));

        for (String table : new String[] {"app_user", "account", "holding", "ai_credential", "guide_request"}) {
            assertThat(count(table, a)).as(table).isZero();
        }
        assertThat(count("account", b)).isEqualTo(1);
        assertThat(count("ai_credential", b)).isEqualTo(1);
        WIREMOCK.verify(
                1,
                postRequestedFor(urlPathEqualTo("/v1/user/unlink"))
                        .withHeader("Authorization", equalTo("KakaoAK " + KakaoStubs.ADMIN_KEY))
                        .withRequestBody(containing("target_id_type=user_id"))
                        .withRequestBody(containing("target_id=" + KAKAO_A)));
        assertThat(jdbc.sql("SELECT count(*) FROM kakao_unlink_queue")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void htmxWithdrawalRedirectsViaHeader() throws Exception {
        kakao.unlinkOk();
        mockMvc.perform(post("/settings/account/withdraw")
                        .with(as(a))
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(header().string("HX-Redirect", "/login?withdrawn"));
    }

    @Test
    void failedUnlinkIsQueuedAndRetriedWithBackoff() throws Exception {
        kakao.unlinkFails(503);
        mockMvc.perform(post("/settings/account/withdraw").with(as(a)).with(csrf()))
                .andExpect(redirectedUrl("/login?withdrawn"));
        assertThat(count("app_user", a)).as("연결 해제 실패와 관계없이 탈퇴는 끝난다").isZero();
        assertThat(jdbc.sql("SELECT attempts FROM kakao_unlink_queue WHERE kakao_id = :k")
                        .param("k", KAKAO_A)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);

        retryScheduler.retry();
        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo("/v1/user/unlink")));

        clock.advance(Duration.ofMinutes(1));
        retryScheduler.retry();
        assertThat(jdbc.sql("SELECT attempts FROM kakao_unlink_queue")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);

        WIREMOCK.resetAll();
        kakao.unlinkOk();
        clock.advance(Duration.ofMinutes(2));
        retryScheduler.retry();
        assertThat(jdbc.sql("SELECT count(*) FROM kakao_unlink_queue")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void retriesStopAfterTenAttempts() {
        kakao.unlinkFails(500);
        jdbc.sql("INSERT INTO kakao_unlink_queue (kakao_id, attempts, next_attempt_at) VALUES (99, 9, :now)")
                .param("now", java.sql.Timestamp.from(clock.instant()))
                .update();

        retryScheduler.retry();
        clock.advance(Duration.ofDays(1));
        retryScheduler.retry();

        WIREMOCK.verify(1, postRequestedFor(urlPathEqualTo("/v1/user/unlink")));
        assertThat(jdbc.sql("SELECT attempts FROM kakao_unlink_queue")
                        .query(Integer.class)
                        .single())
                .isEqualTo(10);
    }

    @Test
    void alreadyUnlinkedUserCountsAsSuccess() throws Exception {
        WIREMOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/v1/user/unlink"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"msg\":\"NotRegisteredUserException\",\"code\":-101}")));

        mockMvc.perform(post("/settings/account/withdraw").with(as(a)).with(csrf()));

        assertThat(jdbc.sql("SELECT count(*) FROM kakao_unlink_queue")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void sameKakaoAccountSignsUpAgainAsNewUser() throws Exception {
        kakao.unlinkOk();
        mockMvc.perform(post("/settings/account/withdraw").with(as(a)).with(csrf()));

        UserId again = userService.signIn(new KakaoId(KAKAO_A), DisplayName.of("다시 온 사용자"));

        assertThat(again).isNotEqualTo(a);
        assertThat(count("account", again)).isZero();
    }

    @Test
    void settingsPageAndGuards() throws Exception {
        mockMvc.perform(get("/settings/account").with(as(a)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("탈퇴하기")));
        mockMvc.perform(post("/settings/account/withdraw").with(as(a))).andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/account/withdraw").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
        assertThat(count("app_user", a)).isEqualTo(1);
    }
}
