package com.stockmanagement.market.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.port.IndicatorStore;
import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 시장 지표 갱신(003 T009, T016). 시계: 2026-11-05(목) 10:00 KST, 국내 장중. */
class MarketIndicatorRefresherIT extends IntegrationTestBase {

    @Autowired
    private MarketIndicatorRefresher refresher;

    @Autowired
    private IndicatorStore store;

    @BeforeEach
    void stubs() {
        refresher.resetSchedule();
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/market-calendar/KR"))
                        .willReturn(
                                json(
                                        "{\"result\":{\"today\":{\"date\":\"2026-11-05\",\"integrated\":{\"regularMarket\":"
                                                + "{\"startTime\":\"2026-11-05T09:00:00+09:00\",\"endTime\":\"2026-11-05T15:30:00+09:00\"}}},"
                                                + "\"previousBusinessDay\":{\"date\":\"2026-11-04\"},\"nextBusinessDay\":{\"date\":\"2026-11-06\"}}}")));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/market-indicators/prices"))
                        .willReturn(
                                json(
                                        "{\"result\":["
                                                + "{\"symbol\":\"KOSPI\",\"timestamp\":\"2026-11-05T09:59:00+09:00\",\"lastPrice\":\"2812.45\"},"
                                                + "{\"symbol\":\"KOSDAQ\",\"timestamp\":\"2026-11-05T09:59:00+09:00\",\"lastPrice\":\"870.10\"}]}")));
        stubCandles("KOSPI", "2790.10");
        stubCandles("KOSDAQ", "875.00");
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/exchange-rate")).atPriority(5).willReturn(json(rate("1375.00"))));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/exchange-rate"))
                .withQueryParam("dateTime", equalTo("2026-11-04T06:30:00Z"))
                .atPriority(1)
                .willReturn(json(rate("1368.20"))));
    }

    private Map<MarketIndicator, IndicatorQuote> saved() {
        return store.findAll().stream().collect(Collectors.toMap(IndicatorQuote::indicator, Function.identity()));
    }

    @Test
    void domesticIndicesAndUsdKrwAreSavedWithPreviousValues() {
        refresher.refreshDomestic();

        Map<MarketIndicator, IndicatorQuote> saved = saved();
        assertThat(saved).containsOnlyKeys(MarketIndicator.KOSPI, MarketIndicator.KOSDAQ, MarketIndicator.USD_KRW);
        assertThat(saved.get(MarketIndicator.KOSPI).value()).isEqualByComparingTo("2812.45");
        assertThat(saved.get(MarketIndicator.KOSPI).previousClose()).isEqualByComparingTo("2790.10");
        assertThat(saved.get(MarketIndicator.KOSPI).asOfDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(saved.get(MarketIndicator.USD_KRW).value()).isEqualByComparingTo("1375.00");
        assertThat(saved.get(MarketIndicator.USD_KRW).previousClose()).isEqualByComparingTo("1368.20");
    }

    @Test
    void previousValuesAreFetchedOncePerDay() {
        refresher.refreshDomestic();
        clock.advance(Duration.ofMinutes(1));
        refresher.refreshDomestic();

        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/market-indicators/KOSPI/candles")));
        WIREMOCK.verify(
                1,
                getRequestedFor(urlPathEqualTo("/api/v1/exchange-rate"))
                        .withQueryParam("dateTime", equalTo("2026-11-04T06:30:00Z")));
        WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/market-indicators/prices")));
    }

    @Test
    void oneFailureKeepsOthersAndPreviousValue() {
        refresher.refreshDomestic();
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/exchange-rate")).atPriority(1).willReturn(serverError()));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/market-indicators/prices"))
                        .willReturn(
                                json(
                                        "{\"result\":[{\"symbol\":\"KOSPI\",\"timestamp\":\"2026-11-05T10:00:00+09:00\",\"lastPrice\":\"2820\"}]}")));
        clock.advance(Duration.ofMinutes(1));

        refresher.refreshDomestic();

        Map<MarketIndicator, IndicatorQuote> saved = saved();
        assertThat(saved.get(MarketIndicator.KOSPI).value()).isEqualByComparingTo("2820");
        assertThat(saved.get(MarketIndicator.USD_KRW).value())
                .as("실패한 지표는 기존 값 유지")
                .isEqualByComparingTo("1375.00");
    }

    @Test
    void outsideMarketHoursRefreshesAtClosedInterval() {
        clock.advance(Duration.ofHours(8)); // 18:00 KST, 장 마감
        refresher.refreshDomestic();
        clock.advance(Duration.ofMinutes(5));
        refresher.refreshDomestic();
        clock.advance(Duration.ofMinutes(30));
        refresher.refreshDomestic();

        WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/market-indicators/prices")));
    }

    @Test
    void globalIndicatorsComeFromFredDailyClose() {
        for (String series : new String[] {"NASDAQCOM", "SP500", "DJIA", "DCOILWTICO", "VIXCLS"}) {
            WIREMOCK.stubFor(
                    get(urlPathEqualTo("/fred/series/observations"))
                            .withQueryParam("series_id", equalTo(series))
                            .willReturn(
                                    json(
                                            "{\"observations\":[{\"date\":\"2026-11-04\",\"value\":\"100.5\"},"
                                                    + "{\"date\":\"2026-11-03\",\"value\":\".\"},{\"date\":\"2026-11-02\",\"value\":\"99.5\"}]}")));
        }

        refresher.refreshGlobal();

        Map<MarketIndicator, IndicatorQuote> saved = saved();
        assertThat(saved)
                .containsOnlyKeys(
                        MarketIndicator.NASDAQ,
                        MarketIndicator.SP500,
                        MarketIndicator.DOW,
                        MarketIndicator.WTI,
                        MarketIndicator.VIX);
        assertThat(saved.get(MarketIndicator.VIX).value()).isEqualByComparingTo("100.5");
        assertThat(saved.get(MarketIndicator.VIX).previousClose())
                .as("결측(.)을 건너뛴 직전 값")
                .isEqualByComparingTo("99.5");
        assertThat(saved.get(MarketIndicator.VIX).asOfDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    private static void stubCandles(String symbol, String previousClose) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-indicators/" + symbol + "/candles"))
                .willReturn(json("{\"result\":{\"candles\":["
                        + "{\"timestamp\":\"2026-11-05T00:00:00+09:00\",\"closePrice\":\"1\"},"
                        + "{\"timestamp\":\"2026-11-04T00:00:00+09:00\",\"closePrice\":\"" + previousClose
                        + "\"}]}}")));
    }

    private static String rate(String mid) {
        return "{\"result\":{\"midRate\":\"" + mid + "\",\"validFrom\":\"2026-11-05T09:59:00+09:00\"}}";
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
