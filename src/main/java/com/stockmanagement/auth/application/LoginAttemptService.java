package com.stockmanagement.auth.application;

import com.stockmanagement.auth.application.port.OwnerStore;
import com.stockmanagement.auth.domain.LoginDecision;
import com.stockmanagement.auth.domain.LoginLockPolicy;
import com.stockmanagement.auth.domain.Owner;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 로그인 시도와 잠금 처리(FR-001a). */
@Service
public class LoginAttemptService {

    private final OwnerStore ownerStore;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final LoginLockPolicy policy = new LoginLockPolicy();

    public LoginAttemptService(OwnerStore ownerStore, PasswordEncoder passwordEncoder, Clock clock) {
        this.ownerStore = ownerStore;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public LoginDecision attempt(String rawPassword) {
        Owner owner = ownerStore.loadForUpdate().orElse(null);
        if (owner == null) {
            return LoginDecision.BAD_CREDENTIALS;
        }
        Instant now = clock.instant();
        boolean matches = !owner.isLocked(now)
                && rawPassword != null
                && passwordEncoder.matches(rawPassword, owner.passwordHash());
        LoginDecision decision = policy.attempt(owner, matches, now);
        ownerStore.update(owner, now);
        return decision;
    }

    @Transactional(readOnly = true)
    public long remainingLockMinutes() {
        Instant now = clock.instant();
        return ownerStore.load().map(o -> o.remainingLockMinutes(now)).orElse(0L);
    }
}
