package com.stockmanagement.market.adapter.cboe;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.cboe: Cboe 지수 일별 종가 CSV(키 불필요). */
@ConfigurationProperties("app.cboe")
public record CboeProperties(String baseUrl, Duration timeout) {}
