package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.DailyCandle;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 가이드 분석용 일봉(T102): 최신순 정렬, 최대 120봉, 금액 문자열 그대로 BigDecimal. */
class TossDailyCandlesIT extends IntegrationTestBase {

    @Autowired
    private MarketDataProvider provider;

    @BeforeEach
    void stubToken() {
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void returnsNewestFirstWithExactDecimals() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("interval", equalTo("1d"))
                .withQueryParam("count", equalTo("60"))
                .willReturn(json("{\"result\":{\"candles\":["
                        + candle("2026-11-03T00:00:00-05:00", "188.1000")
                        + "," + candle("2026-11-04T00:00:00-05:00", "190.0500")
                        + "],\"nextBefore\":null}}")));

        List<DailyCandle> candles = provider.fetchDailyCandles("AAPL", 60);

        assertThat(candles)
                .extracting(DailyCandle::date)
                .containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3));
        assertThat(candles.getFirst().close().toPlainString()).isEqualTo("190.0500");
        assertThat(candles.getFirst().volume().toPlainString()).isEqualTo("1000");
    }

    @Test
    void requestsAtMostOneHundredTwentyCandles() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles")).willReturn(json("{\"result\":{\"candles\":[]}}")));

        assertThat(provider.fetchDailyCandles("005930", 120)).isEmpty();
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/candles")).withQueryParam("count", equalTo("120")));
        assertThatThrownBy(() -> provider.fetchDailyCandles("005930", 121))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String candle(String timestamp, String close) {
        return "{\"timestamp\":\"" + timestamp + "\",\"openPrice\":\"" + close + "\",\"highPrice\":\"" + close
                + "\",\"lowPrice\":\"" + close + "\",\"closePrice\":\"" + close
                + "\",\"volume\":\"1000\",\"currency\":\"USD\"}";
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
