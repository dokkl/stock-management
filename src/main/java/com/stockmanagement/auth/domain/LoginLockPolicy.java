package com.stockmanagement.auth.domain;

import java.time.Duration;
import java.time.Instant;

/** FR-001a: 연속 5회 실패 시 15분 잠금. 잠금 중에는 올바른 비밀번호도 거부한다. */
public final class LoginLockPolicy {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    public LoginDecision attempt(Owner owner, boolean passwordMatches, Instant now) {
        if (owner.isLocked(now)) {
            return LoginDecision.LOCKED;
        }
        owner.clearExpiredLock(now);
        if (passwordMatches) {
            owner.reset();
            return LoginDecision.SUCCESS;
        }
        owner.recordFailure();
        if (owner.failedAttempts() >= MAX_FAILURES) {
            owner.lock(now.plus(LOCK_DURATION));
            return LoginDecision.LOCKED;
        }
        return LoginDecision.BAD_CREDENTIALS;
    }
}
