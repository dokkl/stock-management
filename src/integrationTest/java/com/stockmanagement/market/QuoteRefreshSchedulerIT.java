package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.MarketCalendarCache;
import com.stockmanagement.market.application.QuoteRefreshScheduler;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 시세 스케줄러(research R3). 기본 시각 2026-11-05 10:00 KST: 국내는 장중, 미국(11-04 장)은 마감 후다.
 */
class QuoteRefreshSchedulerIT extends IntegrationTestBase {

    @Autowired
    private QuoteRefreshScheduler scheduler;

    @Autowired
    private MarketCalendarCache calendarCache;

    private Fixtures f;
    private long samsung;

    @BeforeEach
    void setUp() {
        ((Map<?, ?>) ReflectionTestUtils.getField(calendarCache, "cache")).clear();
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-calendar/KR"))
                .willReturn(json(krCalendar("2026-11-05T09:00:00+09:00", "2026-11-05T15:30:00+09:00"))));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/market-calendar/US"))
                        .willReturn(
                                json(
                                        "{\"result\":{\"today\":{\"date\":\"2026-11-04\",\"regularMarket\":"
                                                + "{\"startTime\":\"2026-11-04T23:30:00+09:00\",\"endTime\":\"2026-11-05T06:00:00+09:00\"}},"
                                                + "\"previousBusinessDay\":{\"date\":\"2026-11-03\"},\"nextBusinessDay\":{\"date\":\"2026-11-05\"}}}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks")).willReturn(json("{\"result\":[]}")));
        stubPrice("005930", "71000");
        stubCandles("005930", "2026-11-04", "70000");

        f = new Fixtures(jdbc);
        samsung = f.samsung();
        long account = f.account("키움 일반");
        f.holding(account, samsung, "71500", "10");
    }

    @Test
    void openMarketFetchesOnlyHeldSecurities() {
        f.security("KOSPI", "000660", "SK하이닉스", "SK hynix");

        scheduler.refresh();

        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/prices")));
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/prices")).withQueryParam("symbols", equalTo("005930")));
        assertThat(quoteRow(samsung))
                .containsEntry("price", new BigDecimal("71000.0000"))
                .containsEntry("prev_close", new BigDecimal("70000.0000"))
                .containsEntry("market_state", "OPEN")
                .containsEntry("fetched_at", Timestamp.from(clock.instant()));
    }

    @Test
    void previousCloseIsFetchedOncePerSession() {
        scheduler.refresh();
        clock.advance(Duration.ofSeconds(30));
        stubPrice("005930", "71500");

        scheduler.refresh();

        WIREMOCK.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/prices")));
        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/candles")));
        assertThat(quoteRow(samsung)).containsEntry("price", new BigDecimal("71500.0000"));
    }

    @Test
    void failedFetchKeepsPreviousValueAndFetchedAt() {
        scheduler.refresh();
        Instant firstFetch = clock.instant();
        clock.advance(Duration.ofSeconds(90));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/prices")).willReturn(aResponse().withStatus(500)));

        scheduler.refresh();

        assertThat(quoteRow(samsung))
                .containsEntry("price", new BigDecimal("71000.0000"))
                .containsEntry("fetched_at", Timestamp.from(firstFetch));
    }

    @Test
    void closedMarketFinalizesCloseOnlyOnce() {
        long apple = f.apple();
        f.holding(f.account("미래에셋 해외"), apple, "185.2", "0.35");
        stubPrice("AAPL", "187.50");
        stubCandles("AAPL", "2026-11-03", "190.00");

        scheduler.refresh();
        clock.advance(Duration.ofSeconds(30));
        scheduler.refresh();

        WIREMOCK.verify(
                1, getRequestedFor(urlPathEqualTo("/api/v1/prices")).withQueryParam("symbols", equalTo("AAPL")));
        assertThat(quoteRow(apple))
                .containsEntry("market_state", "CLOSED")
                .containsEntry("prev_close", new BigDecimal("190.0000"));
    }

    @Test
    void preOpenKeepsFinalizedQuoteAndUpdatesState() {
        f.quote(samsung, "71000", "70000", "CLOSED", Instant.parse("2026-11-04T07:00:00Z"));
        jdbc.sql("UPDATE quote SET session_date = DATE '2026-11-04'").update();
        clock.set(Instant.parse("2026-11-04T23:00:00Z")); // 11-05 08:00 KST, 장 시작 전

        scheduler.refresh();

        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/prices")));
        assertThat(quoteRow(samsung)).containsEntry("market_state", "PRE_OPEN");
    }

    @Test
    void marksSuspendedSecurity() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks"))
                .willReturn(json("{\"result\":[{\"symbol\":\"005930\",\"koreanMarketDetail\":"
                        + "{\"krxTradingSuspended\":true,\"nxtTradingSuspended\":null}}]}")));

        scheduler.refresh();

        assertThat(tradingStatus(samsung)).isEqualTo("SUSPENDED");
    }

    @Test
    void calendarFailureSkipsMarket() {
        WIREMOCK.stubFor(get(urlPathMatching("/api/v1/market-calendar/.*"))
                .willReturn(aResponse().withStatus(503)));

        scheduler.refresh();

        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/prices")));
        assertThat(jdbc.sql("SELECT count(*) FROM quote").query(Long.class).single())
                .isZero();
    }

    @Test
    void noHoldingsMeansNoCalls() {
        jdbc.sql("DELETE FROM holding").update();

        scheduler.refresh();

        WIREMOCK.verify(0, anyRequestedFor(urlPathMatching("/api/.*")));
    }

    private Map<String, Object> quoteRow(long securityId) {
        return jdbc.sql("SELECT price, prev_close, market_state, fetched_at FROM quote WHERE security_id = :id")
                .param("id", securityId)
                .query()
                .singleRow();
    }

    private String tradingStatus(long securityId) {
        return jdbc.sql("SELECT trading_status FROM security WHERE id = :id")
                .param("id", securityId)
                .query(String.class)
                .single();
    }

    private static void stubPrice(String symbol, String price) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/prices"))
                .withQueryParam("symbols", equalTo(symbol))
                .willReturn(json("{\"result\":[{\"symbol\":\"" + symbol + "\",\"timestamp\":null,\"lastPrice\":\""
                        + price + "\",\"currency\":\"KRW\"}]}")));
    }

    private static void stubCandles(String symbol, String date, String close) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .withQueryParam("symbol", equalTo(symbol))
                .willReturn(json("{\"result\":{\"candles\":[{\"timestamp\":\"" + date + "T00:00:00+09:00\","
                        + "\"closePrice\":\"" + close + "\"}],\"nextBefore\":null}}")));
    }

    private static String krCalendar(String open, String close) {
        return "{\"result\":{\"today\":{\"date\":\"2026-11-05\",\"integrated\":{\"regularMarket\":"
                + "{\"startTime\":\"" + open + "\",\"endTime\":\"" + close + "\"}}},"
                + "\"previousBusinessDay\":{\"date\":\"2026-11-04\"},\"nextBusinessDay\":{\"date\":\"2026-11-06\"}}}";
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
