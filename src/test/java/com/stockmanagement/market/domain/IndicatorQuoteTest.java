package com.stockmanagement.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.common.domain.Direction;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IndicatorQuoteTest {

    private static final Instant NOW = Instant.parse("2026-11-06T04:00:00Z");
    private static final Duration STALE = Duration.ofMinutes(10);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    private static IndicatorQuote quote(MarketIndicator indicator, String value, String prev, Instant fetchedAt) {
        return new IndicatorQuote(
                indicator, new BigDecimal(value), prev == null ? null : new BigDecimal(prev), NOW, DAY, fetchedAt);
    }

    @Test
    void changeRateAndDirection() {
        IndicatorQuote up = quote(MarketIndicator.KOSPI, "2812.45", "2790.10", NOW);
        assertThat(up.hasChange()).isTrue();
        assertThat(up.change()).isEqualByComparingTo("22.35");
        assertThat(up.changeRate()).isEqualByComparingTo("0.0080");
        assertThat(up.direction()).isEqualTo(Direction.UP);

        assertThat(quote(MarketIndicator.VIX, "15", "16", NOW).direction()).isEqualTo(Direction.DOWN);
        assertThat(quote(MarketIndicator.WTI, "70", "70", NOW).direction()).isEqualTo(Direction.FLAT);
    }

    @Test
    void withoutPreviousCloseHasNoChange() {
        IndicatorQuote q = quote(MarketIndicator.SP500, "6000", null, NOW);
        assertThat(q.hasChange()).isFalse();
        assertThat(q.direction()).isEqualTo(Direction.FLAT);
        assertThat(q.change()).isEqualByComparingTo("0");
        assertThat(q.changeRate()).isEqualByComparingTo("0");
    }

    @Test
    void zeroPreviousCloseGivesZeroRate() {
        assertThat(quote(MarketIndicator.WTI, "1", "0", NOW).changeRate()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsNonPositiveValueAndMissingFields() {
        assertThatThrownBy(() -> quote(MarketIndicator.KOSPI, "0", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IndicatorQuote(null, BigDecimal.ONE, null, NOW, DAY, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void domesticStatusFollowsMarketStateAndFreshness() {
        IndicatorQuote fresh = quote(MarketIndicator.KOSPI, "2800", "2790", NOW.minus(Duration.ofMinutes(5)));
        IndicatorQuote old = quote(MarketIndicator.USD_KRW, "1380", "1375", NOW.minus(Duration.ofMinutes(11)));

        assertThat(fresh.status(MarketState.OPEN, NOW, STALE)).isEqualTo(IndicatorStatus.LIVE);
        assertThat(old.status(MarketState.OPEN, NOW, STALE)).isEqualTo(IndicatorStatus.STALE);
        assertThat(fresh.status(MarketState.CLOSED, NOW, STALE)).isEqualTo(IndicatorStatus.CLOSED);
        assertThat(fresh.status(MarketState.PRE_OPEN, NOW, STALE)).isEqualTo(IndicatorStatus.CLOSED);
        assertThat(fresh.status(null, NOW, STALE)).isEqualTo(IndicatorStatus.CLOSED);
    }

    @Test
    void globalIndicatorsAreDailyClose() {
        assertThat(quote(MarketIndicator.NASDAQ, "23000", "22900", NOW.minus(Duration.ofDays(1)))
                        .status(MarketState.OPEN, NOW, STALE))
                .isEqualTo(IndicatorStatus.DAILY_CLOSE);
    }

    @Test
    void indicatorCatalogIsOrderedAndTyped() {
        assertThat(MarketIndicator.values())
                .containsExactly(
                        MarketIndicator.KOSPI,
                        MarketIndicator.KOSDAQ,
                        MarketIndicator.USD_KRW,
                        MarketIndicator.NASDAQ,
                        MarketIndicator.SP500,
                        MarketIndicator.DOW,
                        MarketIndicator.WTI,
                        MarketIndicator.VIX);
        assertThat(MarketIndicator.USD_KRW.isRealtime()).isTrue();
        assertThat(MarketIndicator.VIX.isRealtime()).isFalse();
        assertThat(MarketIndicator.WTI.unit()).isEqualTo(MarketIndicator.Unit.USD_PER_BARREL);
    }
}
