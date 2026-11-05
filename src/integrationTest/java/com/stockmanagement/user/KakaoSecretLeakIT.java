package com.stockmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.support.KakaoStubs;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/** 카카오 비밀값 비노출(T045): 로그인·탈퇴 흐름의 로그에 인가 코드·토큰·클라이언트 시크릿·어드민 키가 없다. */
@ExtendWith(OutputCaptureExtension.class)
class KakaoSecretLeakIT extends IntegrationTestBase {

    @Test
    void loginAndWithdrawalNeverLogKakaoSecrets(CapturedOutput output) throws Exception {
        KakaoStubs kakao = new KakaoStubs(WIREMOCK).user(15001, "비밀확인").unlinkFails(503);
        MvcResult start = mockMvc.perform(get("/oauth2/authorization/kakao")).andReturn();
        String state = URLDecoder.decode(
                UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
                        .build()
                        .getQueryParams()
                        .getFirst("state"),
                StandardCharsets.UTF_8);
        MvcResult login = mockMvc.perform(get("/login/oauth2/code/kakao")
                        .param("code", "test-code-15001")
                        .param("state", state)
                        .session((MockHttpSession) start.getRequest().getSession()))
                .andReturn();
        assertThat(login.getResponse().getRedirectedUrl()).isEqualTo("/");
        mockMvc.perform(post("/settings/account/withdraw")
                .with(as(userWithKakao(15001)))
                .with(csrf()));
        assertThat(kakao).isNotNull();

        assertThat(output.getAll())
                .doesNotContain("test-code-15001")
                .doesNotContain("token-15001")
                .doesNotContain("refresh-15001")
                .doesNotContain("test-kakao-secret")
                .doesNotContain(KakaoStubs.ADMIN_KEY);
    }

    @Test
    void logPatternMasksKakaoAdminKeyHeader(CapturedOutput output) {
        LoggerFactory.getLogger(KakaoSecretLeakIT.class).warn("헤더: Authorization: KakaoAK abcdef1234567890");

        assertThat(output.getAll()).contains("Authorization: ****").doesNotContain("abcdef1234567890");
    }
}
