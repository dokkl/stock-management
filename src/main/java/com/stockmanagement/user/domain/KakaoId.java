package com.stockmanagement.user.domain;

/** 카카오 회원번호(앱 단위 고유·불변, research R2). */
public record KakaoId(long value) {

    public KakaoId {
        if (value <= 0) {
            throw new IllegalArgumentException("카카오 회원번호는 양수입니다: " + value);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
