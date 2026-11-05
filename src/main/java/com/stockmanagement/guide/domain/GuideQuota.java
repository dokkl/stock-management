package com.stockmanagement.guide.domain;

import com.stockmanagement.common.domain.KstDate;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 종목별 하루 가이드 한도(FR-018, FR-019). 실패하지 않은(진행 중·성공) 요청만 센다.
 *
 * @param used 오늘(KST) 이 종목의 실패하지 않은 요청 수
 */
public record GuideQuota(int used) {

    public static final int DAILY_LIMIT = 2;

    public int remaining() {
        return Math.max(0, DAILY_LIMIT - used);
    }

    public boolean isExhausted() {
        return remaining() == 0;
    }

    /** 한도가 다시 채워지는 시각: 다음 날 00:00 KST. */
    public static Instant resetsAt(LocalDate usageDate) {
        return usageDate.plusDays(1).atStartOfDay(KstDate.ZONE).toInstant();
    }
}
