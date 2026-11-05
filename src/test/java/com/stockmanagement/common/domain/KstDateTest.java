package com.stockmanagement.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.support.MutableClock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KstDateTest {

    @Test
    void lastSecondBeforeKstMidnightIsSameDay() {
        assertThat(KstDate.today(MutableClock.at("2026-11-05T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void kstMidnightIsNextDay() {
        assertThat(KstDate.today(MutableClock.at("2026-11-05T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void zoneIsSeoul() {
        assertThat(KstDate.ZONE.getId()).isEqualTo("Asia/Seoul");
    }
}
