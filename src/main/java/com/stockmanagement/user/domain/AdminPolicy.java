package com.stockmanagement.user.domain;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 관리자 판별: 설정(ADMIN_KAKAO_IDS)에 적힌 카카오 회원번호가 관리자다. 관리자만 공용 AI 키를 관리한다. */
public final class AdminPolicy {

    private final Set<Long> kakaoIds;

    private AdminPolicy(Set<Long> kakaoIds) {
        this.kakaoIds = kakaoIds;
    }

    public static AdminPolicy of(Collection<Long> kakaoIds) {
        return new AdminPolicy(kakaoIds == null ? Set.of() : kakaoIds.stream().collect(Collectors.toUnmodifiableSet()));
    }

    public boolean isAdmin(KakaoId kakaoId) {
        return kakaoIds.contains(kakaoId.value());
    }
}
