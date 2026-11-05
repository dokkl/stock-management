package com.stockmanagement.auth.persistence;

import com.stockmanagement.auth.application.port.OwnerStore;
import com.stockmanagement.auth.domain.Owner;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaOwnerStore implements OwnerStore {

    private final OwnerJpaRepository repository;

    JpaOwnerStore(OwnerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists() {
        return repository.existsById(OwnerEntity.SINGLE_ID);
    }

    @Override
    public Optional<Owner> loadForUpdate() {
        return repository.findSingleForUpdate().map(JpaOwnerStore::toDomain);
    }

    @Override
    public Optional<Owner> load() {
        return repository.findById(OwnerEntity.SINGLE_ID).map(JpaOwnerStore::toDomain);
    }

    @Override
    public void insert(Owner owner, Instant now) {
        repository.save(new OwnerEntity(owner.passwordHash(), now));
    }

    @Override
    public void update(Owner owner, Instant now) {
        OwnerEntity entity = repository.findById(OwnerEntity.SINGLE_ID).orElseThrow();
        entity.applyLoginState(owner.failedAttempts(), owner.lockedUntil(), now);
    }

    private static Owner toDomain(OwnerEntity e) {
        return Owner.restore(e.getPasswordHash(), e.getFailedAttempts(), e.getLockedUntil());
    }
}
