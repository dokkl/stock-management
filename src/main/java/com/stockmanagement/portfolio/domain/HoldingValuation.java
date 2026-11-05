package com.stockmanagement.portfolio.domain;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.common.domain.Direction;
import com.stockmanagement.common.domain.Money;
import java.math.BigDecimal;

/** 보유 종목 평가(data-model.md holding 파생값). 수익률은 소수 넷째 자리 HALF_UP. */
public record HoldingValuation(Money costBasis, Money marketValue) {

    public static HoldingValuation of(
            BigDecimal avgPrice, BigDecimal quantity, BigDecimal currentPrice, Currency currency) {
        return new HoldingValuation(
                new Money(avgPrice.multiply(quantity), currency), new Money(currentPrice.multiply(quantity), currency));
    }

    public Money unrealizedPnl() {
        return marketValue.minus(costBasis);
    }

    public BigDecimal returnRate() {
        return unrealizedPnl().ratioTo(costBasis);
    }

    public Direction direction() {
        return unrealizedPnl().direction();
    }
}
