package com.stockmanagement.market;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** GET /fragments/quotes 계약(contracts/web-routes.md): OOB 셀, 등락 색·기호, 시세 상태 문구, 인증. */
class QuoteFragmentIT extends IntegrationTestBase {

    private Fixtures f;
    private long account;
    private long samsung;

    @BeforeEach
    void setUp() {
        f = new Fixtures(jdbc);
        account = f.account("키움 일반");
        samsung = f.samsung();
    }

    @Test
    void risingQuoteHasUpClassArrowAndLiveTime() throws Exception {
        long holding = f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "OPEN", clock.instant().minusSeconds(5));

        quotes().andExpect(status().isOk())
                .andExpect(content()
                        .string(allOf(
                                containsString("id=\"quote-" + holding + "\""),
                                containsString("id=\"pnl-" + holding + "\""),
                                containsString("hx-swap-oob=\"true\""),
                                containsString("class=\"change up\""),
                                containsString("▲ 1,000 (+1.43%)"),
                                containsString("전일 대비 상승"),
                                containsString("09:59:55 기준"),
                                containsString("class=\"down\""),
                                containsString("▼ ₩5,000 (−0.70%)"))));
    }

    @Test
    void fallingAndFlatQuotes() throws Exception {
        long apple = f.apple();
        f.holding(account, apple, "185.2", "0.35");
        f.quote(apple, "187.50", "190.00", "OPEN", clock.instant());
        f.holding(account, samsung, "70000", "1");
        f.quote(samsung, "70000", "70000", "OPEN", clock.instant());

        quotes().andExpect(content()
                .string(allOf(
                        containsString("class=\"change down\""),
                        containsString("▼ 2.50 (−1.32%)"),
                        containsString("class=\"change flat\""),
                        containsString("– 0 (0.00%)"),
                        containsString("전일 대비 보합"))));
    }

    @Test
    void oldQuoteWhileOpenIsStaleButKeepsLastValue() throws Exception {
        f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "OPEN", clock.instant().minus(Duration.ofMinutes(2)));

        quotes().andExpect(content().string(allOf(containsString("지연 (09:58 기준)"), containsString("₩71,000"))));
    }

    @Test
    void missingQuoteIsUnavailable() throws Exception {
        f.holding(account, samsung, "71500", "10");

        quotes().andExpect(content().string(allOf(containsString("시세 없음"), not(containsString("class=\"change")))));
    }

    @Test
    void closedMarketShowsClosedLabel() throws Exception {
        f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "CLOSED", Instant.parse("2026-11-04T07:00:00Z"));

        quotes().andExpect(content().string(allOf(containsString("장 마감"), not(containsString("지연")))));
    }

    @Test
    void preOpenShowsPreOpenLabel() throws Exception {
        f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "PRE_OPEN", Instant.parse("2026-11-04T07:00:00Z"));

        quotes().andExpect(content().string(containsString("장 시작 전")));
    }

    @Test
    void suspendedSecurityShowsSuspendedLabel() throws Exception {
        long suspended = f.security("KOSDAQ", "123450", "정지종목", null, "SUSPENDED");
        f.holding(account, suspended, "1000", "1");
        f.quote(suspended, "900", "1000", "OPEN", clock.instant());

        quotes().andExpect(content().string(allOf(containsString("거래정지"), containsString("₩900"))));
    }

    @Test
    void dashboardRendersSameCellsAndPoller() throws Exception {
        f.holding(account, samsung, "71500", "10");
        f.quote(samsung, "71000", "70000", "OPEN", clock.instant());

        mockMvc.perform(get("/").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(allOf(
                                containsString("hx-get=\"/fragments/quotes\""),
                                containsString("hx-trigger=\"every 30s\""),
                                containsString("▲ 1,000 (+1.43%)"),
                                not(containsString("hx-swap-oob=\"true\"")))));
    }

    @Test
    void unauthenticatedHtmxRequestIs401() throws Exception {
        createOwner();
        mockMvc.perform(get("/fragments/quotes").header("HX-Request", "true")).andExpect(status().isUnauthorized());
    }

    private ResultActions quotes() throws Exception {
        return mockMvc.perform(get("/fragments/quotes").with(owner()).header("HX-Request", "true"));
    }
}
