package com.stockmanagement.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트에서 시간을 조작하기 위한 Clock (헌법 원칙 II: 결정적 테스트). */
public final class MutableClock extends Clock {

    private volatile Instant now;
    private final ZoneId zone;

    public MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant), ZoneId.of("Asia/Seoul"));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now, newZone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
