package com.stockmanagement.auth.application.port;

import com.stockmanagement.auth.domain.Owner;
import java.time.Instant;
import java.util.Optional;

/** 소유자 저장소 포트. 소유자 행은 최대 1개다. */
public interface OwnerStore {

    boolean exists();

    /** 소유자 행을 잠그고 읽는다(로그인 시도 동시성 보호). */
    Optional<Owner> loadForUpdate();

    Optional<Owner> load();

    void insert(Owner owner, Instant now);

    void update(Owner owner, Instant now);
}
