package com.stockmanagement.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.common.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MoneyFormatterTest {

    private final MoneyFormatter formatter = new MoneyFormatter();

    @Test
    void formatsIndicatorValueWithTwoDecimals() {
        assertThat(formatter.indicator(new BigDecimal("2812.4"))).isEqualTo("2,812.40");
        assertThat(formatter.indicator(new BigDecimal("15.235"))).isEqualTo("15.24");
    }

    @Test
    void formatsIndicatorChange() {
        assertThat(formatter.indicatorChange(new BigDecimal("22.35"), new BigDecimal("0.0080")))
                .isEqualTo("▲ 22.35 (+0.80%)");
        assertThat(formatter.indicatorChange(new BigDecimal("-1.5"), new BigDecimal("-0.0011")))
                .isEqualTo("▼ 1.50 (−0.11%)");
    }

    @Test
    void formatsMonthDay() {
        assertThat(formatter.monthDay(LocalDate.of(2026, 11, 4))).isEqualTo("11-04");
        assertThat(formatter.monthDayTime(Instant.parse("2026-11-05T06:30:00Z")))
                .isEqualTo("11-05 15:30");
    }

    @Test
    void formatsKrwPrice() {
        assertThat(formatter.price(new BigDecimal("71000"), Currency.KRW)).isEqualTo("₩71,000");
    }

    @Test
    void formatsUsdPriceWithTwoDecimals() {
        assertThat(formatter.price(new BigDecimal("187.5"), Currency.USD)).isEqualTo("$187.50");
    }

    @Test
    void formatsUsdAveragePriceWithFourDecimals() {
        assertThat(formatter.avgPrice(new BigDecimal("185.2"), Currency.USD)).isEqualTo("$185.2000");
    }

    @Test
    void formatsKrwAveragePriceWithoutDecimals() {
        assertThat(formatter.avgPrice(new BigDecimal("71500"), Currency.KRW)).isEqualTo("₩71,500");
    }

    @Test
    void formatsUpChange() {
        assertThat(formatter.change(new BigDecimal("1000"), new BigDecimal("0.0143"), Currency.KRW))
                .isEqualTo("▲ 1,000 (+1.43%)");
    }

    @Test
    void formatsDownChange() {
        assertThat(formatter.change(new BigDecimal("-2.50"), new BigDecimal("-0.0132"), Currency.USD))
                .isEqualTo("▼ 2.50 (−1.32%)");
    }

    @Test
    void formatsFlatChange() {
        assertThat(formatter.change(BigDecimal.ZERO, BigDecimal.ZERO, Currency.KRW))
                .isEqualTo("– 0 (0.00%)");
    }

    @Test
    void formatsQuantity() {
        assertThat(formatter.quantity(new BigDecimal("10.000000"))).isEqualTo("10주");
        assertThat(formatter.quantity(new BigDecimal("0.350000"))).isEqualTo("0.35주");
    }

    @Test
    void formatsPnlWithArrowAbsoluteAmountAndSignedRate() {
        assertThat(formatter.pnl(new BigDecimal("-5000"), new BigDecimal("-0.0070"), Currency.KRW))
                .isEqualTo("▼ ₩5,000 (−0.70%)");
        assertThat(formatter.pnl(new BigDecimal("1.68"), new BigDecimal("0.0259"), Currency.USD))
                .isEqualTo("▲ $1.68 (+2.59%)");
        assertThat(formatter.pnl(BigDecimal.ZERO, BigDecimal.ZERO, Currency.KRW))
                .isEqualTo("– ₩0 (0.00%)");
    }

    @Test
    void formatsTimesInKst() {
        Instant instant = Instant.parse("2026-11-05T05:32:05Z");
        assertThat(formatter.time(instant)).isEqualTo("14:32:05");
        assertThat(formatter.shortTime(instant)).isEqualTo("14:32");
        assertThat(formatter.dateTime(instant)).isEqualTo("2026-11-05 14:32");
    }
}
