package com.stockmanagement.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DirectionTest {

    @Test
    void positiveIsUp() {
        assertThat(Direction.of(new BigDecimal("0.0001"))).isEqualTo(Direction.UP);
    }

    @Test
    void negativeIsDown() {
        assertThat(Direction.of(new BigDecimal("-2.50"))).isEqualTo(Direction.DOWN);
    }

    @Test
    void zeroIsFlat() {
        assertThat(Direction.of(new BigDecimal("0.00"))).isEqualTo(Direction.FLAT);
    }
}
