package com.stockmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.stockmanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

/** 보안 헤더(T122): CSP, MIME 스니핑 차단, Referrer-Policy, 프레임 차단, 세션 쿠키 HttpOnly·Secure·SameSite=Lax. */
class SecurityHeadersIT extends IntegrationTestBase {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        createOwner();
        mockMvc.perform(get("/login"))
                .andExpect(header().string(
                                "Content-Security-Policy",
                                allOf(
                                        containsString("default-src 'self'"),
                                        containsString("script-src 'self'"),
                                        containsString("object-src 'none'"),
                                        containsString("frame-ancestors 'none'"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void authenticatedPagesAlsoCarryHeaders() throws Exception {
        mockMvc.perform(get("/").with(owner()))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndLax() {
        var cookie = serverProperties.getServlet().getSession().getCookie();
        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(SameSite.LAX);
    }
}
