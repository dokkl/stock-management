package com.stockmanagement.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GuideQuotaTest {

    @Test
    void remainingIsTwoMinusActiveWithFloorZero() {
        assertThat(new GuideQuota(0).remaining()).isEqualTo(2);
        assertThat(new GuideQuota(1).remaining()).isEqualTo(1);
        assertThat(new GuideQuota(2).remaining()).isZero();
        assertThat(new GuideQuota(3).remaining()).isZero();
        assertThat(new GuideQuota(2).isExhausted()).isTrue();
        assertThat(new GuideQuota(1).isExhausted()).isFalse();
    }

    @Test
    void resetsAtNextMidnightKst() {
        assertThat(GuideQuota.resetsAt(LocalDate.of(2026, 11, 5))).isEqualTo(Instant.parse("2026-11-05T15:00:00Z"));
    }
}
