package com.stockmanagement.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 통화가 있는 금액. 부동소수점을 쓰지 않는다(헌법 원칙 I). */
public record Money(BigDecimal amount, Currency currency) {

    public static final int RATIO_SCALE = 4;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    /** 입력 금액을 통화 정밀도 규칙으로 검증해 만든다. */
    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        if (amount.stripTrailingZeros().scale() > currency.maxScale()) {
            throw new DomainValidationException("amount", "money.scale");
        }
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    /** this / other, 소수 넷째 자리 HALF_UP. 분모가 0이면 0. */
    public BigDecimal ratioTo(Money other) {
        requireSameCurrency(other);
        if (other.amount.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE);
        }
        return amount.divide(other.amount, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    public Direction direction() {
        return Direction.of(amount);
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException("통화가 다릅니다: " + currency + " vs " + other.currency);
        }
    }
}
