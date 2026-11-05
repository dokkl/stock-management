package com.stockmanagement.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.common.domain.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HoldingValuationTest {

    @Test
    void krwLoss() {
        HoldingValuation v = HoldingValuation.of(
                new BigDecimal("71500"), new BigDecimal("10"), new BigDecimal("71000"), Currency.KRW);
        assertThat(v.costBasis().amount()).isEqualByComparingTo("715000");
        assertThat(v.marketValue().amount()).isEqualByComparingTo("710000");
        assertThat(v.unrealizedPnl().amount()).isEqualByComparingTo("-5000");
        assertThat(v.returnRate()).isEqualByComparingTo("-0.0070");
        assertThat(v.direction()).isEqualTo(Direction.DOWN);
    }

    @Test
    void usdGainWithFractionalQuantity() {
        HoldingValuation v = HoldingValuation.of(
                new BigDecimal("185.2000"), new BigDecimal("0.35"), new BigDecimal("190.00"), Currency.USD);
        assertThat(v.unrealizedPnl().amount()).isEqualByComparingTo("1.68");
        assertThat(v.returnRate()).isEqualByComparingTo("0.0259");
        assertThat(v.direction()).isEqualTo(Direction.UP);
        assertThat(v.unrealizedPnl().currency()).isEqualTo(Currency.USD);
    }

    @Test
    void breakEven() {
        HoldingValuation v =
                HoldingValuation.of(new BigDecimal("100"), new BigDecimal("3"), new BigDecimal("100"), Currency.KRW);
        assertThat(v.direction()).isEqualTo(Direction.FLAT);
        assertThat(v.returnRate()).isEqualByComparingTo("0");
    }
}
