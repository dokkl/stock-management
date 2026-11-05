package com.stockmanagement.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OwnerJpaRepository extends JpaRepository<OwnerEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OwnerEntity o where o.id = 1")
    Optional<OwnerEntity> findSingleForUpdate();
}
