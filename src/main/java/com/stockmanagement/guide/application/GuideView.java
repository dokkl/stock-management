package com.stockmanagement.guide.application;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.domain.FailureKind;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.guide.domain.GuideStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 화면에 보여 줄 가이드 요청 1건(요청 당시 스냅샷 포함). */
public record GuideView(
        long id,
        long securityId,
        Long holdingId,
        String symbol,
        String securityName,
        LocalDate usageDate,
        GuideStatus status,
        FailureKind failureKind,
        AiProvider provider,
        String model,
        GuideContent content,
        String rawText,
        String accountName,
        BigDecimal price,
        BigDecimal avgPrice,
        BigDecimal quantity,
        BigDecimal returnRate,
        Currency currency,
        Instant requestedAt,
        Instant completedAt) {

    public boolean isPending() {
        return status == GuideStatus.PENDING;
    }

    public boolean isSucceeded() {
        return status == GuideStatus.SUCCEEDED;
    }

    public boolean isFailed() {
        return status == GuideStatus.FAILED;
    }
}
