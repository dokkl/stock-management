package com.stockmanagement.market.adapter.yahoo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.yahoo: 야후 파이낸스 차트 API(비공식, 키 불필요). WTI 근월물 선물 가격에만 쓴다. */
@ConfigurationProperties("app.yahoo")
public record YahooProperties(String baseUrl, Duration timeout) {}
