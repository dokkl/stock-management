package com.stockmanagement.market.adapter.toss;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스증권 Open API 설정(research R2). client id·secret은 환경 변수로 받는다.
 *
 * @param proxyUrl 토스 호출에 쓸 HTTP 프록시(예: http://10.0.0.5:3128). 토스는 등록한 IP만 허용하므로 나가는 IP가 고정되지
 *     않는 운영 환경에서는 고정 IP 프록시를 거친다. 없으면 직접 호출한다.
 */
@ConfigurationProperties("app.toss")
public record TossProperties(String baseUrl, String clientId, String clientSecret, Duration timeout, URI proxyUrl) {

    @Override
    public String toString() {
        return "TossProperties[baseUrl=" + baseUrl + ", clientId=****]";
    }
}
