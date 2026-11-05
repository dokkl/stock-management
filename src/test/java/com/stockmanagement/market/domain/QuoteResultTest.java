package com.stockmanagement.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuoteResultTest {

    @Test
    void successRequiresValues() {
        Instant now = Instant.parse("2026-11-05T01:00:00Z");
        assertThat(new QuoteResult.Success("005930", BigDecimal.TEN, now).symbol())
                .isEqualTo("005930");
        assertThatThrownBy(() -> new QuoteResult.Success("005930", null, now)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void failureRequiresReason() {
        assertThat(new QuoteResult.Failure("AAPL", QuoteResult.Reason.NOT_FOUND).symbol())
                .isEqualTo("AAPL");
        assertThatThrownBy(() -> new QuoteResult.Failure("AAPL", null)).isInstanceOf(NullPointerException.class);
    }
}
