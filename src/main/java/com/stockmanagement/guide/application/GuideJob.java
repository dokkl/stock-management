package com.stockmanagement.guide.application;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.domain.GuideStatus;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.TradingStatus;
import com.stockmanagement.user.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 백그라운드에서 실행할 가이드 요청. 보유 정보는 요청 당시 스냅샷이고, 종목 상태와 전일 종가는 실행 시점 값이다.
 *
 * @param prevClose 실행 시점 전일 종가. 시세가 없으면 null
 */
public record GuideJob(
        long id,
        UserId userId,
        GuideStatus status,
        AiProvider provider,
        String symbol,
        String name,
        Market market,
        Currency currency,
        TradingStatus tradingStatus,
        BigDecimal price,
        BigDecimal prevClose,
        BigDecimal avgPrice,
        BigDecimal quantity,
        Instant requestedAt) {}
