package com.stockmanagement.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 플랫폼의 유일한 사용자(FR-001). 로그인 실패 횟수와 잠금 상태를 가진다. */
public final class Owner {

    private final String passwordHash;
    private int failedAttempts;
    private Instant lockedUntil;

    private Owner(String passwordHash, int failedAttempts, Instant lockedUntil) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        if (failedAttempts < 0) {
            throw new IllegalArgumentException("failedAttempts must be >= 0");
        }
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public static Owner create(String passwordHash, Instant now) {
        Objects.requireNonNull(now, "now");
        return new Owner(passwordHash, 0, null);
    }

    public static Owner restore(String passwordHash, int failedAttempts, Instant lockedUntil) {
        return new Owner(passwordHash, failedAttempts, lockedUntil);
    }

    public String passwordHash() {
        return passwordHash;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** 남은 잠금 시간(분, 올림). 잠금이 아니면 0. */
    public long remainingLockMinutes(Instant now) {
        if (!isLocked(now)) {
            return 0;
        }
        long seconds = Duration.between(now, lockedUntil).toSeconds();
        return (seconds + 59) / 60;
    }

    void recordFailure() {
        failedAttempts++;
    }

    void lock(Instant until) {
        lockedUntil = until;
        failedAttempts = 0;
    }

    void reset() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    void clearExpiredLock(Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            lockedUntil = null;
        }
    }
}
