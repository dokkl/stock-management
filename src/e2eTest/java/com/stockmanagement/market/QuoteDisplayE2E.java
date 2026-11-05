package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import com.stockmanagement.market.application.MarketCalendarCache;
import com.stockmanagement.market.application.QuoteRefreshScheduler;
import com.stockmanagement.support.E2ETestBase;
import com.stockmanagement.support.Fixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/** quickstart S3: 현재가·등락 색상·평가손익, 시세 실패 시 "지연" 표시 (US2). */
class QuoteDisplayE2E extends E2ETestBase {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Autowired
    private QuoteRefreshScheduler scheduler;

    @Autowired
    private MarketCalendarCache calendarCache;

    @BeforeEach
    void seedHoldingsAndQuotes() {
        ((Map<?, ?>) ReflectionTestUtils.getField(calendarCache, "cache")).clear();
        Fixtures f = new Fixtures(jdbc);
        long account = f.account("키움 일반");
        f.holding(account, f.samsung(), "71500", "10");
        f.holding(account, f.security("KOSPI", "000660", "SK하이닉스", "SK hynix"), "180000", "2");
        f.holding(account, f.security("KOSPI", "035420", "NAVER", "NAVER"), "200000", "1");

        stubMarket();
        stubPrices("{\"symbol\":\"005930\",\"timestamp\":null,\"lastPrice\":\"71000\",\"currency\":\"KRW\"},"
                + "{\"symbol\":\"000660\",\"timestamp\":null,\"lastPrice\":\"175000\",\"currency\":\"KRW\"},"
                + "{\"symbol\":\"035420\",\"timestamp\":null,\"lastPrice\":\"200000\",\"currency\":\"KRW\"}");
        stubPreviousClose("005930", "70000");
        stubPreviousClose("000660", "180000");
        stubPreviousClose("035420", "200000");

        scheduler.refresh();
    }

    private Locator row(String name) {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(name));
    }

    private String color(Locator locator) {
        return (String) locator.evaluate("el => getComputedStyle(el).color");
    }

    @Test
    void showsColoredChangesAndPnl() {
        setupAndLogin();

        Locator samsung = row("삼성전자");
        assertThat(samsung).containsText("₩71,000");
        assertThat(samsung).containsText("▲ 1,000 (+1.43%)");
        assertThat(samsung).containsText("▼ ₩5,000 (−0.70%)");
        assertThat(samsung).containsText("기준");
        assertThat(color(samsung.locator(".change"))).isEqualTo("rgb(198, 40, 40)");

        Locator hynix = row("SK하이닉스");
        assertThat(hynix).containsText("▼ 5,000 (−2.78%)");
        assertThat(color(hynix.locator(".change"))).isEqualTo("rgb(21, 101, 192)");

        Locator naver = row("NAVER");
        assertThat(naver).containsText("– 0 (0.00%)");
        assertThat(color(naver.locator(".change"))).isEqualTo("rgb(95, 99, 104)");

        assertNoAccessibilityViolations();
    }

    @Test
    void failedRefreshShowsDelayedWithLastValue() {
        setupAndLogin();
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/v1/prices")).willReturn(aResponse().withStatus(500)));
        scheduler.refresh();
        jdbc.sql(
                        "UPDATE quote SET fetched_at = fetched_at - interval '2 minutes', as_of = as_of - interval '2 minutes'")
                .update();

        // 30초 폴링과 같은 요청을 즉시 보낸다(응답 셀은 hx-swap-oob로 교체된다).
        page.evaluate("() => htmx.ajax('GET', '/fragments/quotes', {source: '#quote-poller', swap: 'none'})");

        Locator samsung = row("삼성전자");
        assertThat(samsung).containsText("지연 (");
        assertThat(samsung).containsText("₩71,000");
        assertNoAccessibilityViolations();
    }

    /** 실제 시계를 쓰므로 지금이 국내 정규장 중이 되도록 장 운영일 응답을 만든다. */
    private static void stubMarket() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String open =
                OffsetDateTime.ofInstant(now.minus(Duration.ofHours(1)), KST).toString();
        String close =
                OffsetDateTime.ofInstant(now.plus(Duration.ofHours(1)), KST).toString();
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/market-calendar/KR"))
                .willReturn(json("{\"result\":{\"today\":{\"date\":\"" + today + "\",\"integrated\":{\"regularMarket\":"
                        + "{\"startTime\":\"" + open + "\",\"endTime\":\"" + close + "\"}}},"
                        + "\"previousBusinessDay\":{\"date\":\"" + today.minusDays(1) + "\"},"
                        + "\"nextBusinessDay\":{\"date\":\"" + today.plusDays(1) + "\"}}}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks")).willReturn(json("{\"result\":[]}")));
    }

    private static void stubPrices(String items) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/prices")).willReturn(json("{\"result\":[" + items + "]}")));
    }

    private static void stubPreviousClose(String symbol, String close) {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/candles"))
                .withQueryParam("symbol", equalTo(symbol))
                .willReturn(json("{\"result\":{\"candles\":[{\"timestamp\":\"" + yesterday + "T00:00:00+09:00\","
                        + "\"closePrice\":\"" + close + "\"}],\"nextBefore\":null}}")));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
