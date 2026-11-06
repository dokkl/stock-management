package com.stockmanagement.market.adapter.fred;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.fred: 미국 연방준비은행(세인트루이스) FRED API(003 research R3). API 키는 로그·화면에 남기지 않는다. */
@ConfigurationProperties("app.fred")
public record FredProperties(String baseUrl, String apiKey, Duration timeout) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "FredProperties[baseUrl=" + baseUrl + ", apiKey=****]";
    }
}
