package com.stockmanagement.market.adapter.toss;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * 토스증권 REST 호출. 모든 호출은 그룹별 레이트 리미터를 거친다.
 * 성공 응답은 {@code {"result": ...}} 봉투로 오며 여기서 벗겨낸다(토큰 발급은 OAuth2 표준 형식).
 */
@Component
public class TossHttpClient {

    private final RestClient restClient;
    private final TossRateLimiter rateLimiter = new TossRateLimiter();
    private final TossProperties properties;

    public TossHttpClient(RestClient.Builder builder, TossProperties properties) {
        this.properties = properties;
        HttpClient.Builder http =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.timeout());
        if (properties.proxyUrl() != null) {
            URI proxy = properties.proxyUrl();
            http.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(proxy.getHost(), proxy.getPort())));
        }
        HttpClient httpClient = http.build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    TokenResponse issueToken() {
        rateLimiter.acquire(TossRateLimiter.Group.AUTH);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return restClient
                .post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    /** 인증된 GET. 응답 봉투의 result를 반환한다. */
    <T> T get(
            TossRateLimiter.Group group,
            String accessToken,
            Function<UriBuilder, URI> uri,
            ParameterizedTypeReference<Envelope<T>> type) {
        rateLimiter.acquire(group);
        Envelope<T> envelope = restClient
                .get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(type);
        if (envelope == null || envelope.result() == null) {
            throw new IllegalStateException("토스증권 응답에 result가 없습니다");
        }
        return envelope.result();
    }

    /** 성공 응답 봉투. */
    record Envelope<T>(T result) {}

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {

        @Override
        public String toString() {
            return "TokenResponse[expiresIn=" + expiresIn + "]";
        }
    }
}
