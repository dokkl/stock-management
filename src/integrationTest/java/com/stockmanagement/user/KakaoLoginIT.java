package com.stockmanagement.user;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.support.KakaoStubs;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

/** 카카오 로그인 흐름(T017, contracts/web-routes.md 인증): 인가 → 콜백 → 토큰 → 사용자 정보 → 가입/로그인. */
class KakaoLoginIT extends IntegrationTestBase {

    private final KakaoStubs kakao = new KakaoStubs(WIREMOCK);

    /** 인가 요청을 시작하고 (세션, state)를 돌려준다. */
    private Object[] startAuthorization() throws Exception {
        MvcResult start = mockMvc.perform(MockMvcRequestBuilders.get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = start.getResponse().getRedirectedUrl();
        assertThat(location)
                .startsWith(WIREMOCK.baseUrl() + "/oauth/authorize")
                .contains("client_id=test-kakao-client");
        String state = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("state");
        return new Object[] {start.getRequest().getSession(), URLDecoder.decode(state, StandardCharsets.UTF_8)};
    }

    private MvcResult callback(MockHttpSession session, String... params) throws Exception {
        var request = MockMvcRequestBuilders.get("/login/oauth2/code/kakao").session(session);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult login(long kakaoId) throws Exception {
        Object[] auth = startAuthorization();
        return callback((MockHttpSession) auth[0], "code", "test-code-" + kakaoId, "state", (String) auth[1]);
    }

    private Map<String, Object> userRow(long kakaoId) {
        return jdbc.sql("SELECT id, display_name, last_login_at FROM app_user WHERE kakao_id = :k")
                .param("k", kakaoId)
                .query()
                .singleRow();
    }

    @Test
    void firstLoginSignsUpAndRedirectsToDashboard() throws Exception {
        kakao.user(1001, "홍길동");

        MvcResult result = login(1001);

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(userRow(1001)).containsEntry("display_name", "홍길동");
    }

    @Test
    void secondLoginReusesUser() throws Exception {
        kakao.user(1001, "홍길동");
        login(1001);
        clock.advance(java.time.Duration.ofHours(1));
        Object[] auth = startAuthorization();
        MvcResult again = callback((MockHttpSession) auth[0], "code", "test-code-1001", "state", (String) auth[1]);

        assertThat(again.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single())
                .isEqualTo(1);
        assertThat(userRow(1001).get("last_login_at").toString()).contains("2026-11-05");
    }

    @Test
    void missingNicknameUsesDefaultName() throws Exception {
        kakao.user(1002, null);
        login(1002);
        assertThat(userRow(1002)).containsEntry("display_name", "카카오 사용자");
    }

    @Test
    void cancelledConsentRedirectsWithCancelledMessage() throws Exception {
        Object[] auth = startAuthorization();
        MvcResult result = callback((MockHttpSession) auth[0], "error", "access_denied", "state", (String) auth[1]);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=cancelled");
        assertThat(jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single())
                .isZero();
    }

    @Test
    void stateMismatchFails() throws Exception {
        kakao.user(1001, "홍길동");
        Object[] auth = startAuthorization();
        MvcResult result = callback((MockHttpSession) auth[0], "code", "test-code-1001", "state", "forged");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=failed");
        assertThat(jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single())
                .isZero();
    }

    @Test
    void userInfoWithoutIdFails() throws Exception {
        kakao.user(1003, "누구");
        WIREMOCK.stubFor(get(urlPathEqualTo("/v2/user/me"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"properties\":{}}")));

        MvcResult result = login(1003);

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=failed");
    }

    @Test
    void loginPageHasOnlyKakaoButtonAndPasswordLoginIsGone() throws Exception {
        String html = mockMvc.perform(MockMvcRequestBuilders.get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(html).contains("카카오로 시작하기", "/oauth2/authorization/kakao").doesNotContain("type=\"password\"");
        mockMvc.perform(MockMvcRequestBuilders.get("/setup").with(owner())).andExpect(status().isNotFound());
        mockMvc.perform(post("/login").with(csrf()).param("password", "x")).andExpect(status().is4xxClientError());
    }

    @Test
    void logoutEndsSessionAndRedirects() throws Exception {
        mockMvc.perform(post("/logout").with(owner()).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }
}
