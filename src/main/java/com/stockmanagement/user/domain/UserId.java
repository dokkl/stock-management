package com.stockmanagement.user.domain;

/** 앱 사용자 식별자. 사용자 소유 데이터(계좌·AI 키·가이드)의 모든 조회·변경은 이 값으로 범위를 정한다. */
public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new IllegalArgumentException("UserId는 양수입니다: " + value);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
