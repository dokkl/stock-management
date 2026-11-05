package com.stockmanagement.market.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 시장의 오늘 장 운영 정보(토스증권 장 운영일 API). 정규장 시작·종료 시각은 서머타임이 반영된 절대 시각이다.
 * 휴장일이면 시작·종료가 없다.
 *
 * @param today 시장 현지 기준 오늘
 * @param previousBusinessDay 직전 영업일
 */
public record MarketCalendar(
        Market market, LocalDate today, Instant regularOpen, Instant regularClose, LocalDate previousBusinessDay) {

    public MarketCalendar {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(previousBusinessDay, "previousBusinessDay");
        if ((regularOpen == null) != (regularClose == null)) {
            throw new IllegalArgumentException("정규장 시작·종료 시각은 함께 있어야 합니다");
        }
    }

    public boolean isBusinessDay() {
        return regularOpen != null;
    }

    public MarketState state(Instant now) {
        if (!isBusinessDay()) {
            return MarketState.HOLIDAY;
        }
        if (now.isBefore(regularOpen)) {
            return MarketState.PRE_OPEN;
        }
        return now.isBefore(regularClose) ? MarketState.OPEN : MarketState.CLOSED;
    }

    /** 전일 종가의 기준이 되는 거래일: 영업일이면 오늘, 휴장일이면 직전 영업일. */
    public LocalDate referenceDate() {
        return isBusinessDay() ? today : previousBusinessDay;
    }

    /** 지금 시각에 화면에 보일 시세가 속한 거래일. 장 시작 전에는 아직 직전 영업일의 시세다. */
    public LocalDate sessionDate(Instant now) {
        return state(now) == MarketState.PRE_OPEN ? previousBusinessDay : referenceDate();
    }
}
