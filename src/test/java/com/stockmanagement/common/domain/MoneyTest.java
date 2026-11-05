package com.stockmanagement.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void krwRejectsFractionalAmount() {
        assertThatThrownBy(() -> Money.of("71500.5", Currency.KRW))
                .isInstanceOf(DomainValidationException.class)
                .extracting("messageKey")
                .isEqualTo("money.scale");
    }

    @Test
    void krwAcceptsIntegerWithTrailingZeros() {
        assertThat(Money.of("71500.00", Currency.KRW).amount()).isEqualByComparingTo("71500");
    }

    @Test
    void usdAcceptsUpToFourDecimals() {
        assertThat(Money.of("185.2025", Currency.USD).amount()).isEqualByComparingTo("185.2025");
    }

    @Test
    void usdRejectsFiveDecimals() {
        assertThatThrownBy(() -> Money.of("185.20251", Currency.USD)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void operationsAcrossCurrenciesFail() {
        Money krw = Money.of("100", Currency.KRW);
        Money usd = Money.of("1", Currency.USD);
        assertThatThrownBy(() -> krw.plus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> krw.minus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> krw.ratioTo(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusMinusTimes() {
        Money a = Money.of("71500", Currency.KRW);
        assertThat(a.plus(Money.of("500", Currency.KRW)).amount()).isEqualByComparingTo("72000");
        assertThat(a.minus(Money.of("72000", Currency.KRW)).amount()).isEqualByComparingTo("-500");
        assertThat(Money.of("185.20", Currency.USD)
                        .times(new BigDecimal("0.35"))
                        .amount())
                .isEqualByComparingTo("64.8200");
    }

    @Test
    void ratioRoundsHalfUpToFourDecimals() {
        Money pnl = Money.of("-5000", Currency.KRW);
        Money cost = Money.of("715000", Currency.KRW);
        assertThat(pnl.ratioTo(cost)).isEqualByComparingTo("-0.0070");
    }

    @Test
    void ratioToZeroIsZero() {
        assertThat(Money.of("10", Currency.KRW).ratioTo(Money.of("0", Currency.KRW)))
                .isEqualByComparingTo("0");
    }

    @Test
    void nullsRejected() {
        assertThatThrownBy(() -> new Money(null, Currency.KRW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void signReflectsAmount() {
        assertThat(Money.of("-1", Currency.KRW).direction()).isEqualTo(Direction.DOWN);
    }
}
