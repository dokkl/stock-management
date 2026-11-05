package com.stockmanagement.user.application.port;

import com.stockmanagement.user.domain.AppUser;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.KakaoId;
import com.stockmanagement.user.domain.UserId;
import java.time.Instant;
import java.util.Optional;

/** 사용자 저장소(app_user). */
public interface UserStore {

    Optional<AppUser> find(UserId id);

    Optional<AppUser> findByKakaoId(KakaoId kakaoId);

    /**
     * 레거시 사용자(기능 001 데이터를 담은 미인수 행)를 이 카카오 사용자가 차지한다. 동시에 여러 명이 시도해도 한 명만
     * 성공한다(행 잠금). 레거시 행이 없거나 이미 차지되었으면 비어 있다.
     */
    Optional<UserId> claimLegacy(KakaoId kakaoId, DisplayName displayName, Instant now);

    UserId insert(KakaoId kakaoId, DisplayName displayName, Instant now);

    void touchLogin(UserId id, DisplayName displayName, Instant now);

    /** 같은 사용자의 계좌·보유 생성 경쟁을 직렬화하기 위해 사용자 행을 잠근다(현재 트랜잭션 끝까지). */
    void lock(UserId id);

    /** 사용자와 소유 데이터(계좌·보유·AI 키·가이드)를 연쇄 삭제한다. 없었으면 false. */
    boolean delete(UserId id);
}
