package com.stockmanagement.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.common.domain.DomainValidationException;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Security;
import com.stockmanagement.market.domain.TradingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HoldingTest {

    private static final Security SAMSUNG =
            new Security(1L, Exchange.KOSPI, "005930", "삼성전자", "Samsung Electronics", TradingStatus.ACTIVE);
    private static final Security APPLE =
            new Security(2L, Exchange.NASDAQ, "AAPL", "애플", "Apple Inc.", TradingStatus.ACTIVE);

    private static HoldingTerms terms(Security security, String avgPrice, String quantity) {
        return HoldingTerms.of(security, new BigDecimal(avgPrice), new BigDecimal(quantity));
    }

    private static void assertRejected(Runnable action, String field, String key) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainValidationException.class)
                .hasFieldOrPropertyWithValue("field", field)
                .hasFieldOrPropertyWithValue("messageKey", key);
    }

    @Test
    void acceptsKoreanHolding() {
        HoldingTerms t = terms(SAMSUNG, "71500", "10");
        assertThat(t.avgPrice().amount()).isEqualByComparingTo("71500");
        assertThat(t.quantity()).isEqualByComparingTo("10");
    }

    @Test
    void acceptsUsHoldingWithFractions() {
        HoldingTerms t = terms(APPLE, "185.2025", "0.350001");
        assertThat(t.avgPrice().currency()).isEqualTo(com.stockmanagement.common.domain.Currency.USD);
    }

    @Test
    void avgPriceMustBePositive() {
        assertRejected(() -> terms(SAMSUNG, "0", "10"), "avgPrice", "holding.avgPrice.positive");
        assertRejected(() -> terms(SAMSUNG, "-1", "10"), "avgPrice", "holding.avgPrice.positive");
    }

    @Test
    void quantityMustBePositive() {
        assertRejected(() -> terms(SAMSUNG, "71500", "0"), "quantity", "holding.quantity.positive");
    }

    @Test
    void krwAvgPriceHasNoDecimals() {
        assertRejected(() -> terms(SAMSUNG, "71500.5", "10"), "avgPrice", "holding.avgPrice.scale");
    }

    @Test
    void usdAvgPriceAtMostFourDecimals() {
        assertRejected(() -> terms(APPLE, "185.20251", "1"), "avgPrice", "holding.avgPrice.scale");
    }

    @Test
    void koreanQuantityMustBeInteger() {
        assertRejected(() -> terms(SAMSUNG, "71500", "1.5"), "quantity", "holding.quantity.integer");
    }

    @Test
    void usQuantityAtMostSixDecimals() {
        assertRejected(() -> terms(APPLE, "185", "0.1234567"), "quantity", "holding.quantity.scale");
    }

    @Test
    void missingValuesRejected() {
        assertRejected(() -> HoldingTerms.of(SAMSUNG, null, BigDecimal.ONE), "avgPrice", "holding.avgPrice.required");
        assertRejected(() -> HoldingTerms.of(SAMSUNG, BigDecimal.ONE, null), "quantity", "holding.quantity.required");
    }

    @Test
    void holdingLimitAllowsBelowMaximum() {
        new HoldingLimitPolicy(500).checkCanAdd(499);
    }

    @Test
    void holdingLimitRejectsAtMaximum() {
        assertThatThrownBy(() -> new HoldingLimitPolicy(500).checkCanAdd(500))
                .isInstanceOf(HoldingLimitExceededException.class)
                .hasFieldOrPropertyWithValue("messageKey", "holding.limit");
    }
}
