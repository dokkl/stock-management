package com.stockmanagement.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "owner")
class OwnerEntity {

    static final long SINGLE_ID = 1L;

    @Id
    private Long id;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "selected_ai_provider")
    private String selectedAiProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OwnerEntity() {}

    OwnerEntity(String passwordHash, Instant now) {
        this.id = SINGLE_ID;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    int getFailedAttempts() {
        return failedAttempts;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    void applyLoginState(int failedAttempts, Instant lockedUntil, Instant now) {
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.updatedAt = now;
    }
}
