package com.stockmanagement.common.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** 업무 날짜 기준은 한국 시간(KST)이다. */
public final class KstDate {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstDate() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }
}
