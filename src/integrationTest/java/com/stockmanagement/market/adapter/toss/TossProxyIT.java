package com.stockmanagement.market.adapter.toss;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 토스 API는 등록한 IP만 허용하므로 운영에서는 고정 IP 프록시(app.toss.proxy-url)를 거쳐 호출한다. WireMock을 HTTP 프록시로 띄워
 * 존재하지 않는 호스트(toss.invalid)로의 요청이 프록시를 통해서만 성공함을 확인한다.
 */
class TossProxyIT {

    private static final String TOSS = "http://toss.invalid";

    private WireMockServer proxy;

    @BeforeEach
    void startProxy() {
        proxy = new WireMockServer(options().dynamicPort().enableBrowserProxying(true));
        proxy.start();
        proxy.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"via-proxy\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @AfterEach
    void stopProxy() {
        proxy.stop();
    }

    private static TossHttpClient client(URI proxyUrl) {
        return new TossHttpClient(
                RestClient.builder(), new TossProperties(TOSS, "id", "secret", Duration.ofSeconds(2), proxyUrl));
    }

    @Test
    void callsGoThroughConfiguredProxy() {
        TossHttpClient.TokenResponse token = client(URI.create(proxy.baseUrl())).issueToken();

        assertThat(token.accessToken()).isEqualTo("via-proxy");
        assertThat(proxy.getAllServeEvents())
                .singleElement()
                .satisfies(e -> assertThat(e.getRequest().getHost()).isEqualTo("toss.invalid"));
    }

    @Test
    void withoutProxyCallsTossDirectly() {
        assertThatThrownBy(() -> client(null).issueToken()).isInstanceOf(ResourceAccessException.class);
        assertThat(proxy.getAllServeEvents()).isEmpty();
    }
}
