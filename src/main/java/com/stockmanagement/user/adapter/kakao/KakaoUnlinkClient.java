package com.stockmanagement.user.adapter.kakao;

import com.stockmanagement.user.application.UnlinkResult;
import com.stockmanagement.user.application.port.KakaoUnlinker;
import com.stockmanagement.user.domain.KakaoId;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 어드민 키로 카카오 연결을 해제한다(research R7). 사용자 토큰을 저장하지 않아도 된다. 어드민 키와 응답 본문은 로그에
 * 남기지 않는다. 이미 연결이 끊긴 사용자(카카오 오류 코드 -101)는 성공으로 본다.
 */
@Component
class KakaoUnlinkClient implements KakaoUnlinker {

    private static final Logger log = LoggerFactory.getLogger(KakaoUnlinkClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String NOT_REGISTERED = "-101";

    private final RestClient restClient;
    private final String adminKey;

    KakaoUnlinkClient(
            RestClient.Builder builder,
            @Value("${app.kakao.api-base-url}") String apiBaseUrl,
            @Value("${app.kakao.admin-key:}") String adminKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.restClient =
                builder.clone().baseUrl(apiBaseUrl).requestFactory(factory).build();
        this.adminKey = adminKey;
    }

    @Override
    public UnlinkResult unlink(KakaoId kakaoId) {
        if (adminKey == null || adminKey.isBlank()) {
            return UnlinkResult.failed("admin key not configured");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", Long.toString(kakaoId.value()));
        try {
            restClient
                    .post()
                    .uri("/v1/user/unlink")
                    .header("Authorization", "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return UnlinkResult.ok();
        } catch (HttpClientErrorException e) {
            if (e.getResponseBodyAsString().contains("\"code\":" + NOT_REGISTERED)) {
                return UnlinkResult.ok();
            }
            return failed(e);
        } catch (RestClientResponseException e) {
            return failed(e);
        } catch (RestClientException e) {
            log.warn("카카오 연결 해제 실패: type={}", e.getClass().getSimpleName());
            return UnlinkResult.failed(e.getClass().getSimpleName());
        }
    }

    private static UnlinkResult failed(RestClientResponseException e) {
        log.warn("카카오 연결 해제 실패: status={}", e.getStatusCode().value());
        return UnlinkResult.failed("HTTP " + e.getStatusCode().value());
    }
}
