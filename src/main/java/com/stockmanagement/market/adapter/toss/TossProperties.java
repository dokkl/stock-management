package com.stockmanagement.market.adapter.toss;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 토스증권 Open API 설정(research R2). client id·secret은 환경 변수로 받는다. */
@ConfigurationProperties("app.toss")
public record TossProperties(String baseUrl, String clientId, String clientSecret, Duration timeout) {

    @Override
    public String toString() {
        return "TossProperties[baseUrl=" + baseUrl + ", clientId=****]";
    }
}
