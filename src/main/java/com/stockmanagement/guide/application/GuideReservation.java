package com.stockmanagement.guide.application;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 슬롯 예약에 함께 저장할 요청 당시 스냅샷(FR-025, data-model.md 스냅샷 원칙). */
public record GuideReservation(
        long securityId,
        long holdingId,
        long accountId,
        String accountName,
        LocalDate usageDate,
        AiProvider provider,
        BigDecimal price,
        BigDecimal avgPrice,
        BigDecimal quantity,
        BigDecimal returnRate,
        Currency currency,
        Instant requestedAt) {}
