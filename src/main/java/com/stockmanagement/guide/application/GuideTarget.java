package com.stockmanagement.guide.application;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.TradingStatus;
import java.math.BigDecimal;

/**
 * 가이드를 요청할 보유 종목과 현재 상태.
 *
 * @param price 저장된 현재가. 시세가 없으면 null
 * @param prevClose 저장된 전일 종가. 시세가 없으면 null
 */
public record GuideTarget(
        long holdingId,
        long accountId,
        String accountName,
        long securityId,
        String symbol,
        String name,
        Market market,
        Currency currency,
        TradingStatus tradingStatus,
        BigDecimal avgPrice,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal prevClose) {

    public boolean isActive() {
        return tradingStatus == TradingStatus.ACTIVE;
    }

    public boolean hasQuote() {
        return price != null;
    }
}
