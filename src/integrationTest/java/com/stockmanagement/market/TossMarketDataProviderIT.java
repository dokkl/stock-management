package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketCalendar;
import com.stockmanagement.market.domain.QuoteResult;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 토스증권 시세 어댑터: 현재가 배치 조회, 부분 실패, 장 운영일, 전일 종가(일봉). */
class TossMarketDataProviderIT extends IntegrationTestBase {

    @Autowired
    private MarketDataProvider provider;

    @BeforeEach
    void stubToken() {
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void parsesKoreanAndUsPricesAsExactDecimals() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/prices"))
                        .withQueryParam("symbols", equalTo("005930,AAPL"))
                        .willReturn(
                                json(
                                        "{\"result\":["
                                                + "{\"symbol\":\"005930\",\"timestamp\":\"2026-11-05T10:00:00+09:00\",\"lastPrice\":\"71000\",\"currency\":\"KRW\"},"
                                                + "{\"symbol\":\"AAPL\",\"timestamp\":null,\"lastPrice\":\"187.5000\",\"currency\":\"USD\"}]}")));

        List<QuoteResult> results = provider.fetchQuotes(List.of("005930", "AAPL"));

        assertThat(results).hasSize(2).allMatch(r -> r instanceof QuoteResult.Success);
        QuoteResult.Success samsung = (QuoteResult.Success) results.get(0);
        QuoteResult.Success apple = (QuoteResult.Success) results.get(1);
        assertThat(samsung.price()).isEqualByComparingTo("71000");
        assertThat(samsung.asOf())
                .isEqualTo(OffsetDateTime.parse("2026-11-05T10:00:00+09:00").toInstant());
        assertThat(apple.price().toPlainString()).isEqualTo("187.5000");
        assertThat(apple.asOf()).as("timestamp가 null이면 조회 시각").isEqualTo(clock.instant());
    }

    @Test
    void missingSymbolIsNotFound() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/prices"))
                        .willReturn(
                                json(
                                        "{\"result\":[{\"symbol\":\"005930\",\"timestamp\":null,\"lastPrice\":\"1\",\"currency\":\"KRW\"}]}")));

        List<QuoteResult> results = provider.fetchQuotes(List.of("005930", "ZZZZ"));

        assertThat(results.get(1)).isEqualTo(new QuoteResult.Failure("ZZZZ", QuoteResult.Reason.NOT_FOUND));
    }

    @Test
    void serverErrorFailsBatchAsUpstreamError() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/prices")).willReturn(aResponse().withStatus(500)));
        assertThat(provider.fetchQuotes(List.of("005930")))
                .containsExactly(new QuoteResult.Failure("005930", QuoteResult.Reason.UPSTREAM_ERROR));
    }

    @Test
    void tooManyRequestsIsRateLimited() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/prices"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"rate-limit-exceeded\",\"message\":\"\"}}")));
        assertThat(provider.fetchQuotes(List.of("005930")))
                .containsExactly(new QuoteResult.Failure("005930", QuoteResult.Reason.RATE_LIMITED));
    }

    @Test
    void splitsIntoBatchesOfTwoHundred() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/prices")).willReturn(json("{\"result\":[]}")));
        List<String> symbols =
                IntStream.range(0, 250).mapToObj(i -> String.format("%06d", i)).toList();

        assertThat(provider.fetchQuotes(symbols)).hasSize(250);
        WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/prices")));
    }

    @Test
    void parsesKoreanCalendar() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-calendar/KR"))
                .willReturn(json("{\"result\":{"
                        + "\"today\":{\"date\":\"2026-11-05\",\"integrated\":{\"preMarket\":null,"
                        + "\"regularMarket\":{\"startTime\":\"2026-11-05T09:00:00+09:00\",\"endTime\":\"2026-11-05T15:30:00+09:00\"},"
                        + "\"afterMarket\":null}},"
                        + "\"previousBusinessDay\":{\"date\":\"2026-11-04\",\"integrated\":null},"
                        + "\"nextBusinessDay\":{\"date\":\"2026-11-06\",\"integrated\":null}}}")));

        MarketCalendar calendar = provider.fetchMarketCalendar(Market.KR);

        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(calendar.regularOpen())
                .isEqualTo(OffsetDateTime.parse("2026-11-05T09:00:00+09:00").toInstant());
        assertThat(calendar.previousBusinessDay()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    void parsesUsHolidayCalendar() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/market-calendar/US"))
                        .willReturn(
                                json(
                                        "{\"result\":{"
                                                + "\"today\":{\"date\":\"2026-11-26\",\"dayMarket\":null,\"preMarket\":null,\"regularMarket\":null,\"afterMarket\":null},"
                                                + "\"previousBusinessDay\":{\"date\":\"2026-11-25\"},\"nextBusinessDay\":{\"date\":\"2026-11-27\"}}}")));

        MarketCalendar calendar = provider.fetchMarketCalendar(Market.US);

        assertThat(calendar.isBusinessDay()).isFalse();
        assertThat(calendar.referenceDate()).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void previousCloseIsLatestDailyCloseBeforeReferenceDate() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .withQueryParam("symbol", equalTo("005930"))
                .withQueryParam("interval", equalTo("1d"))
                .willReturn(json("{\"result\":{\"candles\":["
                        + candle("2026-11-05T00:00:00+09:00", "71000")
                        + "," + candle("2026-11-04T00:00:00+09:00", "70000")
                        + "," + candle("2026-11-03T00:00:00+09:00", "69500")
                        + "],\"nextBefore\":null}}")));

        assertThat(provider.fetchPreviousClose("005930", LocalDate.of(2026, 11, 5)))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("70000"));
        assertThat(provider.fetchPreviousClose("005930", LocalDate.of(2026, 11, 3)))
                .isEmpty();
    }

    @Test
    void usDailyCandleDateIsLocalDate() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(json("{\"result\":{\"candles\":[" + candle("2026-11-04T00:00:00-05:00", "190.00")
                        + "],\"nextBefore\":null}}")));

        assertThat(provider.fetchPreviousClose("AAPL", LocalDate.of(2026, 11, 5)))
                .hasValueSatisfying(v -> assertThat(v.toPlainString()).isEqualTo("190.00"));
    }

    @Test
    void suspendedSymbolsComeFromKrxTradingSuspendedFlag() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks"))
                .withQueryParam("symbols", equalTo("005930,000660,AAPL"))
                .willReturn(json("{\"result\":["
                        + "{\"symbol\":\"005930\",\"koreanMarketDetail\":{\"krxTradingSuspended\":true}},"
                        + "{\"symbol\":\"000660\",\"koreanMarketDetail\":{\"krxTradingSuspended\":false}},"
                        + "{\"symbol\":\"AAPL\",\"koreanMarketDetail\":null}]}")));

        assertThat(provider.fetchSuspendedSymbols(List.of("005930", "000660", "AAPL")))
                .containsExactly("005930");
    }

    @Test
    void expiredTokenIsReissuedOnce() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/prices"))
                .inScenario("token")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("reissued"));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/prices"))
                        .inScenario("token")
                        .whenScenarioStateIs("reissued")
                        .willReturn(
                                json(
                                        "{\"result\":[{\"symbol\":\"005930\",\"timestamp\":null,\"lastPrice\":\"1\",\"currency\":\"KRW\"}]}")));

        assertThat(provider.fetchQuotes(List.of("005930"))).allMatch(r -> r instanceof QuoteResult.Success);
        WIREMOCK.verify(
                2, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/oauth2/token")));
    }

    private static String candle(String timestamp, String close) {
        return "{\"timestamp\":\"" + timestamp + "\",\"openPrice\":\"" + close + "\",\"highPrice\":\"" + close
                + "\",\"lowPrice\":\"" + close + "\",\"closePrice\":\"" + close
                + "\",\"volume\":\"100\",\"currency\":\"KRW\"}";
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
