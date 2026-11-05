package com.stockmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.support.IntegrationTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

/** FR-001a: 비활성 7일 후 세션 만료, 세션 쿠키 HttpOnly. */
class SessionTimeoutIT extends IntegrationTestBase {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void sessionTimeoutIsSevenDays() {
        assertThat(serverProperties.getServlet().getSession().getTimeout()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void sessionCookieIsHttpOnly() {
        assertThat(serverProperties.getServlet().getSession().getCookie().getHttpOnly())
                .isTrue();
    }
}
