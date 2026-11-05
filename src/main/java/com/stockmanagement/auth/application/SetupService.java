package com.stockmanagement.auth.application;

import com.stockmanagement.auth.application.port.OwnerStore;
import com.stockmanagement.auth.domain.Owner;
import com.stockmanagement.common.domain.DomainValidationException;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최초 1회 소유자 비밀번호 설정(FR-001). */
@Service
public class SetupService {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final OwnerStore ownerStore;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SetupService(OwnerStore ownerStore, PasswordEncoder passwordEncoder, Clock clock) {
        this.ownerStore = ownerStore;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isCompleted() {
        return ownerStore.exists();
    }

    @Transactional
    public void setup(String password, String passwordConfirm) {
        if (ownerStore.exists()) {
            throw new SetupAlreadyCompletedException();
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new DomainValidationException("password", "auth.password.tooShort");
        }
        if (!password.equals(passwordConfirm)) {
            throw new DomainValidationException("passwordConfirm", "auth.password.mismatch");
        }
        ownerStore.insert(Owner.create(passwordEncoder.encode(password), clock.instant()), clock.instant());
    }
}
