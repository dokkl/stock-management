package com.stockmanagement.common.domain;

import java.math.BigDecimal;

/** 등락 방향. 상승 빨강, 하락 파랑, 보합 중립 색으로 표시된다(FR-010). */
public enum Direction {
    UP,
    DOWN,
    FLAT;

    public static Direction of(BigDecimal value) {
        return switch (value.signum()) {
            case 1 -> UP;
            case -1 -> DOWN;
            default -> FLAT;
        };
    }
}
