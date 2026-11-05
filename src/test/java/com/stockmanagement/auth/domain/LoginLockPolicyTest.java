package com.stockmanagement.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginLockPolicyTest {

    private MutableClock clock;
    private Owner owner;
    private final LoginLockPolicy policy = new LoginLockPolicy();

    @BeforeEach
    void setUp() {
        clock = MutableClock.at("2026-11-05T01:00:00Z");
        owner = Owner.create("hash", clock.instant());
    }

    private LoginDecision attempt(boolean passwordMatches) {
        return policy.attempt(owner, passwordMatches, clock.instant());
    }

    @Test
    void fourFailuresDoNotLock() {
        for (int i = 0; i < 4; i++) {
            assertThat(attempt(false)).isEqualTo(LoginDecision.BAD_CREDENTIALS);
        }
        assertThat(owner.failedAttempts()).isEqualTo(4);
        assertThat(owner.isLocked(clock.instant())).isFalse();
    }

    @Test
    void fifthFailureLocksForFifteenMinutesAndResetsCounter() {
        for (int i = 0; i < 4; i++) {
            attempt(false);
        }
        assertThat(attempt(false)).isEqualTo(LoginDecision.LOCKED);
        assertThat(owner.lockedUntil()).isEqualTo(Instant.parse("2026-11-05T01:15:00Z"));
        assertThat(owner.failedAttempts()).isZero();
    }

    @Test
    void lockedRejectsCorrectPassword() {
        for (int i = 0; i < 5; i++) {
            attempt(false);
        }
        clock.advance(Duration.ofMinutes(14));
        assertThat(attempt(true)).isEqualTo(LoginDecision.LOCKED);
    }

    @Test
    void unlocksAfterFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            attempt(false);
        }
        clock.advance(Duration.ofMinutes(15));
        assertThat(attempt(true)).isEqualTo(LoginDecision.SUCCESS);
        assertThat(owner.lockedUntil()).isNull();
    }

    @Test
    void successResetsCounter() {
        attempt(false);
        attempt(false);
        assertThat(attempt(true)).isEqualTo(LoginDecision.SUCCESS);
        assertThat(owner.failedAttempts()).isZero();
    }

    @Test
    void remainingLockMinutesRoundsUp() {
        for (int i = 0; i < 5; i++) {
            attempt(false);
        }
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertThat(owner.remainingLockMinutes(clock.instant())).isEqualTo(5);
        clock.advance(Duration.ofMinutes(5));
        assertThat(owner.remainingLockMinutes(clock.instant())).isZero();
    }
}
