package com.stockmanagement.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.market.application.port.IndicatorStore;
import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.support.QueryCounter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 003 contracts/web-routes.md: 대시보드 지표 영역과 GET /fragments/indicators. */
class MarketIndicatorRoutesIT extends IntegrationTestBase {

    @Autowired
    private IndicatorStore store;

    @Autowired
    private QueryCounter queryCounter;

    private String fragment() throws Exception {
        return mockMvc.perform(get("/fragments/indicators").with(owner()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void dashboardLoadsIndicatorAreaLazily() throws Exception {
        mockMvc.perform(get("/").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(allOf(
                                containsString("id=\"market-indicators\""),
                                containsString("hx-get=\"/fragments/indicators\""),
                                containsString("load, every 60s"),
                                containsString("불러오는 중"))));
    }

    @Test
    void emptyStateWhenNoIndicatorYet() throws Exception {
        assertThat(fragment()).contains("시장 지표를 아직 받지 못했습니다").doesNotContain("class=\"indicator-card");
    }

    @Test
    void cardsShowValueChangeAndBasis() throws Exception {
        Instant fetched = clock.instant();
        store.save(new IndicatorQuote(
                MarketIndicator.KOSPI,
                new BigDecimal("2812.45"),
                new BigDecimal("2790.10"),
                fetched,
                LocalDate.of(2026, 11, 5),
                fetched));
        store.save(new IndicatorQuote(
                MarketIndicator.USD_KRW,
                new BigDecimal("1375.5"),
                new BigDecimal("1380"),
                fetched,
                LocalDate.of(2026, 11, 5),
                fetched));
        store.save(new IndicatorQuote(
                MarketIndicator.VIX,
                new BigDecimal("15.2"),
                new BigDecimal("15.2"),
                Instant.parse("2026-11-04T00:00:00Z"),
                LocalDate.of(2026, 11, 4),
                fetched));

        String html = fragment();

        assertThat(html)
                .contains("코스피", "2,812.45", "▲ 22.35 (+0.80%)", "상승")
                .contains("원·달러", "1,375.50", "원", "▼ 4.50 (−0.33%)", "하락")
                .contains("VIX", "15.20", "– 0.00 (0.00%)", "보합", "11-04 종가")
                .contains("FRED")
                .contains("나스닥 종합", "정보 없음");
        assertThat(html.indexOf("코스피")).isLessThan(html.indexOf("코스닥"));
        assertThat(html.indexOf("다우존스")).isLessThan(html.indexOf("WTI"));
    }

    @Test
    void fragmentReadsStoredValuesOnly() throws Exception {
        owner();
        queryCounter.reset();
        mockMvc.perform(get("/fragments/indicators").with(owner()).header("HX-Request", "true"))
                .andExpect(status().isOk());
        assertThat(queryCounter.count()).as("지표 조회 1회(외부 호출 없음)").isLessThanOrEqualTo(2);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/fragments/indicators").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("코스피"))));
    }
}
