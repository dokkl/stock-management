package com.stockmanagement.user.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 카카오로 가입한 사용자.
 *
 * @param kakaoId 카카오 회원번호. 레거시(기능 001 데이터를 담은 미인수) 사용자만 null
 */
public record AppUser(UserId id, KakaoId kakaoId, DisplayName displayName, Instant createdAt) {

    public AppUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
