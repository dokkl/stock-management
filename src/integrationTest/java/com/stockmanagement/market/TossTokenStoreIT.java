package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.market.adapter.toss.TossTokenStore;
import com.stockmanagement.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 토스증권 OAuth2 Client Credentials 토큰: 발급, DB 캐시 재사용, 만료 전 재발급, 암호화 저장. */
class TossTokenStoreIT extends IntegrationTestBase {

    @Autowired
    private TossTokenStore tokenStore;

    @BeforeEach
    void stubTokenEndpoint() {
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=test-client-id"))
                .withRequestBody(containing("client_secret=test-client-secret"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void issuesTokenOnFirstUse() {
        assertThat(tokenStore.accessToken()).isEqualTo("tok-1");
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/oauth2/token")));
    }

    @Test
    void reusesValidTokenFromDatabase() {
        tokenStore.accessToken();
        clock.advance(Duration.ofMinutes(50));
        assertThat(tokenStore.accessToken()).isEqualTo("tok-1");
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/oauth2/token")));
    }

    @Test
    void reissuesFiveMinutesBeforeExpiry() {
        tokenStore.accessToken();
        clock.advance(Duration.ofMinutes(56));
        tokenStore.accessToken();
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo("/oauth2/token")));
    }

    @Test
    void invalidateForcesReissue() {
        tokenStore.accessToken();
        tokenStore.invalidate();
        tokenStore.accessToken();
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo("/oauth2/token")));
    }

    @Test
    void storedTokenIsEncrypted() {
        tokenStore.accessToken();
        byte[] stored = jdbc.sql("SELECT ciphertext FROM market_token WHERE provider = 'TOSS'")
                .query(byte[].class)
                .single();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("tok-1");
    }
}
