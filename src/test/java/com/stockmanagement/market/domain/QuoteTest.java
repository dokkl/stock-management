package com.stockmanagement.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.common.domain.Direction;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QuoteTest {

    private static final Instant FETCHED = Instant.parse("2026-11-05T01:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofMinutes(1);

    private static Quote quote(String price, String prevClose, MarketState state) {
        return new Quote(
                1L,
                new BigDecimal(price),
                new BigDecimal(prevClose),
                LocalDate.of(2026, 11, 5),
                state,
                FETCHED,
                FETCHED);
    }

    @Test
    void upChange() {
        Quote q = quote("71000", "70000", MarketState.OPEN);
        assertThat(q.change()).isEqualByComparingTo("1000");
        assertThat(q.changeRate()).isEqualByComparingTo("0.0143");
        assertThat(q.direction()).isEqualTo(Direction.UP);
    }

    @Test
    void downChange() {
        Quote q = quote("187.50", "190.00", MarketState.OPEN);
        assertThat(q.change()).isEqualByComparingTo("-2.50");
        assertThat(q.changeRate()).isEqualByComparingTo("-0.0132");
        assertThat(q.direction()).isEqualTo(Direction.DOWN);
    }

    @Test
    void flatChange() {
        Quote q = quote("100", "100", MarketState.OPEN);
        assertThat(q.direction()).isEqualTo(Direction.FLAT);
        assertThat(q.changeRate()).isEqualByComparingTo("0");
    }

    @Test
    void zeroPrevCloseGivesZeroRate() {
        assertThat(quote("100", "0", MarketState.OPEN).changeRate()).isEqualByComparingTo("0");
    }

    @Test
    void liveWhileOpenAndFresh() {
        Quote q = quote("100", "100", MarketState.OPEN);
        assertThat(q.status(FETCHED.plusSeconds(60), STALE_AFTER)).isEqualTo(QuoteStatus.LIVE);
    }

    @Test
    void staleWhenOpenAndOlderThanOneMinute() {
        Quote q = quote("100", "100", MarketState.OPEN);
        assertThat(q.status(FETCHED.plusSeconds(61), STALE_AFTER)).isEqualTo(QuoteStatus.STALE);
    }

    @Test
    void closedAndHolidayAreNeverStale() {
        Instant later = FETCHED.plus(Duration.ofHours(10));
        assertThat(quote("1", "1", MarketState.CLOSED).status(later, STALE_AFTER)).isEqualTo(QuoteStatus.CLOSED);
        assertThat(quote("1", "1", MarketState.HOLIDAY).status(later, STALE_AFTER)).isEqualTo(QuoteStatus.CLOSED);
        assertThat(quote("1", "1", MarketState.PRE_OPEN).status(later, STALE_AFTER)).isEqualTo(QuoteStatus.PRE_OPEN);
    }

    @Test
    void missingQuoteIsUnavailable() {
        assertThat(QuoteStatus.of(null, FETCHED, STALE_AFTER)).isEqualTo(QuoteStatus.UNAVAILABLE);
        assertThat(QuoteStatus.of(quote("1", "1", MarketState.OPEN), FETCHED, STALE_AFTER))
                .isEqualTo(QuoteStatus.LIVE);
    }

    @Test
    void requiresValues() {
        assertThatThrownBy(() -> new Quote(1L, null, BigDecimal.ONE, LocalDate.now(), MarketState.OPEN, FETCHED, FETCHED))
                .isInstanceOf(NullPointerException.class);
    }
}
