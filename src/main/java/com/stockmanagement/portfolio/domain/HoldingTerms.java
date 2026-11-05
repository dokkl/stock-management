package com.stockmanagement.portfolio.domain;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.common.domain.Money;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.Security;
import java.math.BigDecimal;

/**
 * 보유 종목의 평단가·수량(FR-003, FR-005, FR-007). 통화별 정밀도:
 * KRW 평단가 정수, USD 평단가 소수 4자리, KR 수량 정수, US 수량 소수 6자리.
 */
public record HoldingTerms(Money avgPrice, BigDecimal quantity) {

    public static final int US_QUANTITY_SCALE = 6;

    public static HoldingTerms of(Security security, BigDecimal avgPrice, BigDecimal quantity) {
        return new HoldingTerms(
                validAvgPrice(security.currency(), avgPrice), validQuantity(security.market(), quantity));
    }

    private static Money validAvgPrice(Currency currency, BigDecimal avgPrice) {
        if (avgPrice == null) {
            throw new DomainValidationException("avgPrice", "holding.avgPrice.required");
        }
        if (avgPrice.signum() <= 0) {
            throw new DomainValidationException("avgPrice", "holding.avgPrice.positive");
        }
        if (avgPrice.stripTrailingZeros().scale() > currency.maxScale()) {
            throw new DomainValidationException("avgPrice", "holding.avgPrice.scale");
        }
        return Money.of(avgPrice, currency);
    }

    private static BigDecimal validQuantity(Market market, BigDecimal quantity) {
        if (quantity == null) {
            throw new DomainValidationException("quantity", "holding.quantity.required");
        }
        if (quantity.signum() <= 0) {
            throw new DomainValidationException("quantity", "holding.quantity.positive");
        }
        int scale = quantity.stripTrailingZeros().scale();
        if (market == Market.KR && scale > 0) {
            throw new DomainValidationException("quantity", "holding.quantity.integer");
        }
        if (scale > US_QUANTITY_SCALE) {
            throw new DomainValidationException("quantity", "holding.quantity.scale");
        }
        return quantity;
    }
}
