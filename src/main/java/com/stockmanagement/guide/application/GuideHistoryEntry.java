package com.stockmanagement.guide.application;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.domain.GuideAction;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 가이드 기록 목록의 한 줄(FR-025~FR-027). 값은 모두 요청 당시 스냅샷이다.
 *
 * @param action 행동 분류. 구조화되지 않은(원문) 기록이면 null
 * @param headline 결론 한 줄: 종합 판단, 없으면 요약, 원문 기록이면 원문 앞부분
 * @param accountDeleted 기록을 남긴 계좌가 삭제됨
 * @param holdingEnded 보유 종목이 삭제됨(보유 종료)
 */
public record GuideHistoryEntry(
        long id,
        Instant requestedAt,
        AiProvider provider,
        String model,
        GuideAction action,
        String headline,
        String accountName,
        boolean accountDeleted,
        boolean holdingEnded,
        BigDecimal price,
        BigDecimal avgPrice,
        BigDecimal quantity,
        BigDecimal returnRate,
        Currency currency) {}
