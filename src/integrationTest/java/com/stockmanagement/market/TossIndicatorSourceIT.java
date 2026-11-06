package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.IndicatorReading;
import com.stockmanagement.market.application.port.DomesticIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 국내 지수·환율(003 contracts/external-apis.md 토스). */
class TossIndicatorSourceIT extends IntegrationTestBase {

    @Autowired
    private DomesticIndicatorSource source;

    @BeforeEach
    void stubToken() {
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void currentIndicesSkipsMissingTimestamp() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-indicators/prices"))
                .withQueryParam("symbols", equalTo("KOSPI,KOSDAQ"))
                .willReturn(json("""
                        {"result":[
                          {"symbol":"KOSPI","timestamp":"2026-11-06T13:20:00+09:00","lastPrice":"2812.45"},
                          {"symbol":"KOSDAQ","timestamp":null,"lastPrice":"0"}]}""")));

        Map<MarketIndicator, IndicatorReading> indices = source.currentIndices();

        assertThat(indices).containsOnlyKeys(MarketIndicator.KOSPI);
        assertThat(indices.get(MarketIndicator.KOSPI).value()).isEqualByComparingTo("2812.45");
        assertThat(indices.get(MarketIndicator.KOSPI).asOf()).isEqualTo(Instant.parse("2026-11-06T04:20:00Z"));
    }

    @Test
    void previousCloseIsLastCandleBeforeSessionDate() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-indicators/KOSPI/candles"))
                .withQueryParam("interval", equalTo("1d"))
                .willReturn(json("""
                        {"result":{"candles":[
                          {"timestamp":"2026-11-06T00:00:00+09:00","openPrice":"1","highPrice":"1","lowPrice":"1","closePrice":"2812.45","volume":"1"},
                          {"timestamp":"2026-11-05T00:00:00+09:00","openPrice":"1","highPrice":"1","lowPrice":"1","closePrice":"2790.10","volume":"1"}]}}""")));

        assertThat(source.previousIndexClose(MarketIndicator.KOSPI, LocalDate.of(2026, 11, 6)))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("2790.10"));
        assertThat(source.previousIndexClose(MarketIndicator.KOSPI, LocalDate.of(2026, 11, 7)))
                .as("오늘 봉이 아직 없을 때(다음 거래일 장 전)")
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("2812.45"));
    }

    @Test
    void usdKrwUsesMidRateAndPointInTimeQuery() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/exchange-rate"))
                .withQueryParam("baseCurrency", equalTo("USD"))
                .withQueryParam("quoteCurrency", equalTo("KRW"))
                .atPriority(5)
                .willReturn(json(rate("1380.5", "1375.00", "2026-11-06T13:20:00+09:00"))));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/exchange-rate"))
                .withQueryParam("dateTime", equalTo("2026-11-05T06:30:00Z"))
                .atPriority(1)
                .willReturn(json(rate("1372", "1368.20", "2026-11-05T15:30:00+09:00"))));

        assertThat(source.currentUsdKrw()).hasValueSatisfying(r -> {
            assertThat(r.value()).isEqualByComparingTo("1375.00");
            assertThat(r.asOf()).isEqualTo(Instant.parse("2026-11-06T04:20:00Z"));
        });
        assertThat(source.usdKrwAt(Instant.parse("2026-11-05T06:30:00Z")))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1368.20"));
    }

    @Test
    void failuresAreEmptyNotExceptions() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-indicators/prices")).willReturn(serverError()));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/exchange-rate")).willReturn(serverError()));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/market-indicators/KOSDAQ/candles")).willReturn(serverError()));

        assertThat(source.currentIndices()).isEmpty();
        assertThat(source.currentUsdKrw()).isEmpty();
        assertThat(source.previousIndexClose(MarketIndicator.KOSDAQ, LocalDate.of(2026, 11, 6)))
                .isEmpty();
    }

    private static String rate(String rate, String mid, String validFrom) {
        return "{\"result\":{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"KRW\",\"rate\":\"" + rate
                + "\",\"midRate\":\"" + mid
                + "\",\"basisPoint\":\"40\",\"rateChangeType\":\"UP\",\"validFrom\":\"" + validFrom
                + "\",\"validUntil\":\"" + validFrom + "\"}}";
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
