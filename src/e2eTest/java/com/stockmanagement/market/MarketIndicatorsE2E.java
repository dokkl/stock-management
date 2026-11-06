package com.stockmanagement.market;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.stockmanagement.support.E2ETestBase;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 003 quickstart: 대시보드 상단 시장 지표 카드(순서·등락 기호·기준), 모바일 배치, 접근성. */
class MarketIndicatorsE2E extends E2ETestBase {

    @BeforeEach
    void seed() {
        Instant now = Instant.now();
        insert("KOSPI", "2812.45", "2790.10", now, "2026-11-05");
        insert("KOSDAQ", "870.10", "875.00", now, "2026-11-05");
        insert("USD_KRW", "1375.50", "1380.00", now, "2026-11-05");
        insert("NASDAQ", "23051.12", "22990.00", Instant.parse("2026-11-04T00:00:00Z"), "2026-11-04");
        insert("SP500", "6012.34", "5990.00", Instant.parse("2026-11-04T00:00:00Z"), "2026-11-04");
        insert("DOW", "45012.00", "45100.00", Instant.parse("2026-11-04T00:00:00Z"), "2026-11-04");
        insert("WTI", "70.12", "71.00", Instant.parse("2026-11-04T00:00:00Z"), "2026-11-04");
        insert("VIX", "15.20", "15.20", Instant.parse("2026-11-04T00:00:00Z"), "2026-11-04");
    }

    private void insert(String code, String value, String prev, Instant asOf, String date) {
        jdbc.sql("""
                        INSERT INTO market_indicator_quote (indicator, value, previous_close, as_of, as_of_date, fetched_at)
                        VALUES (:c, CAST(:v AS numeric), CAST(:p AS numeric), :a, CAST(:d AS date), now())
                        """)
                .param("c", code)
                .param("v", value)
                .param("p", prev)
                .param("a", java.sql.Timestamp.from(asOf))
                .param("d", date)
                .update();
    }

    @Test
    void cardsAppearInOrderWithDirectionSymbols() {
        setupAndLogin();
        Locator cards = page.locator("#market-indicators .indicator-card");
        assertThat(cards).hasCount(8);
        assertThat(cards.locator(".indicator-name"))
                .hasText(new String[] {"코스피", "코스닥", "원·달러", "나스닥 종합", "S&P 500", "다우존스", "WTI 유가", "VIX 변동성"});
        assertThat(cards.nth(0)).containsText("▲ 22.35 (+0.80%)");
        assertThat(cards.nth(1)).containsText("▼");
        assertThat(cards.nth(7)).containsText("– 0.00 (0.00%)");
        assertThat(cards.nth(4)).containsText("11-04 종가");
        assertThat(page.locator(".indicator-source")).containsText("FRED");
        assertNoAccessibilityViolations();
    }

    @Test
    void mobileHasNoHorizontalOverflow() {
        page.setViewportSize(390, 844);
        setupAndLogin();
        assertThat(page.locator("#market-indicators .indicator-card")).hasCount(8);
        Number overflow = (Number) page.evaluate("() => document.documentElement.scrollWidth - window.innerWidth");
        assertThat(overflow.intValue()).as("가로 스크롤 없음").isLessThanOrEqualTo(0);
        assertNoAccessibilityViolations();
    }
}
