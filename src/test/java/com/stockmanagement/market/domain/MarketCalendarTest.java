package com.stockmanagement.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** 장 상태는 토스증권 장 운영일 응답의 정규장 시작·종료 시각으로 판단한다(서머타임은 응답에 반영되어 있다). */
class MarketCalendarTest {

    private static Instant at(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    private static final MarketCalendar KR = new MarketCalendar(
            Market.KR,
            LocalDate.of(2026, 11, 5),
            at("2026-11-05T09:00:00+09:00"),
            at("2026-11-05T15:30:00+09:00"),
            LocalDate.of(2026, 11, 4));

    @Test
    void krStates() {
        assertThat(KR.state(at("2026-11-05T08:59:59+09:00"))).isEqualTo(MarketState.PRE_OPEN);
        assertThat(KR.state(at("2026-11-05T09:00:00+09:00"))).isEqualTo(MarketState.OPEN);
        assertThat(KR.state(at("2026-11-05T15:29:59+09:00"))).isEqualTo(MarketState.OPEN);
        assertThat(KR.state(at("2026-11-05T15:30:00+09:00"))).isEqualTo(MarketState.CLOSED);
    }

    @Test
    void usSessionCrossesKstMidnightInWinter() {
        MarketCalendar us = new MarketCalendar(
                Market.US,
                LocalDate.of(2026, 11, 5),
                at("2026-11-05T09:30:00-05:00"),
                at("2026-11-05T16:00:00-05:00"),
                LocalDate.of(2026, 11, 4));
        assertThat(us.state(at("2026-11-05T23:29:00+09:00"))).isEqualTo(MarketState.PRE_OPEN);
        assertThat(us.state(at("2026-11-05T23:30:00+09:00"))).isEqualTo(MarketState.OPEN);
        assertThat(us.state(at("2026-11-06T05:59:00+09:00"))).isEqualTo(MarketState.OPEN);
        assertThat(us.state(at("2026-11-06T06:00:00+09:00"))).isEqualTo(MarketState.CLOSED);
    }

    @Test
    void usSessionInSummerTime() {
        MarketCalendar us = new MarketCalendar(
                Market.US,
                LocalDate.of(2026, 3, 9),
                at("2026-03-09T09:30:00-04:00"),
                at("2026-03-09T16:00:00-04:00"),
                LocalDate.of(2026, 3, 6));
        assertThat(us.state(at("2026-03-09T22:30:00+09:00"))).isEqualTo(MarketState.OPEN);
        assertThat(us.state(at("2026-03-09T22:29:00+09:00"))).isEqualTo(MarketState.PRE_OPEN);
    }

    @Test
    void holidayHasNoSession() {
        MarketCalendar holiday =
                new MarketCalendar(Market.KR, LocalDate.of(2026, 5, 5), null, null, LocalDate.of(2026, 5, 4));
        assertThat(holiday.state(at("2026-05-05T10:00:00+09:00"))).isEqualTo(MarketState.HOLIDAY);
        assertThat(holiday.isBusinessDay()).isFalse();
    }

    @Test
    void referenceDateForPreviousClose() {
        assertThat(KR.isBusinessDay()).isTrue();
        assertThat(KR.referenceDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        MarketCalendar holiday =
                new MarketCalendar(Market.KR, LocalDate.of(2026, 5, 5), null, null, LocalDate.of(2026, 5, 4));
        assertThat(holiday.referenceDate()).isEqualTo(LocalDate.of(2026, 5, 4));
    }

    @Test
    void sessionDateIsPreviousBusinessDayBeforeOpen() {
        assertThat(KR.sessionDate(at("2026-11-05T08:59:59+09:00"))).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(KR.sessionDate(at("2026-11-05T09:00:00+09:00"))).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(KR.sessionDate(at("2026-11-05T16:00:00+09:00"))).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void sessionDateOnHolidayIsPreviousBusinessDay() {
        MarketCalendar holiday =
                new MarketCalendar(Market.KR, LocalDate.of(2026, 5, 5), null, null, LocalDate.of(2026, 5, 4));
        assertThat(holiday.sessionDate(at("2026-05-05T10:00:00+09:00"))).isEqualTo(LocalDate.of(2026, 5, 4));
    }

    @Test
    void openAndCloseMustComeTogether() {
        Instant open = at("2026-11-05T09:00:00+09:00");
        LocalDate today = LocalDate.of(2026, 11, 5);
        LocalDate prev = LocalDate.of(2026, 11, 4);
        assertThatThrownBy(() -> new MarketCalendar(Market.KR, today, open, null, prev))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketCalendar(Market.KR, today, null, open, prev))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
