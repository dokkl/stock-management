package com.stockmanagement.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.common.domain.Currency;
import org.junit.jupiter.api.Test;

class SecurityTest {

    @Test
    void marketAndCurrencyFollowExchange() {
        Security samsung = new Security(1L, Exchange.KOSPI, "005930", "삼성전자", null, TradingStatus.ACTIVE);
        Security apple = new Security(2L, Exchange.NASDAQ, "AAPL", null, "Apple Inc.", TradingStatus.ACTIVE);
        assertThat(samsung.market()).isEqualTo(Market.KR);
        assertThat(samsung.currency()).isEqualTo(Currency.KRW);
        assertThat(apple.market()).isEqualTo(Market.US);
        assertThat(apple.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void displayNamePrefersKoreanName() {
        assertThat(new Security(1L, Exchange.NASDAQ, "AAPL", "애플", "Apple", TradingStatus.ACTIVE).displayName())
                .isEqualTo("애플");
        assertThat(new Security(1L, Exchange.NASDAQ, "AAPL", null, "Apple", TradingStatus.ACTIVE).displayName())
                .isEqualTo("Apple");
        assertThat(new Security(1L, Exchange.NASDAQ, "AAPL", " ", "Apple", TradingStatus.ACTIVE).displayName())
                .isEqualTo("Apple");
    }

    @Test
    void requiresAName() {
        assertThatThrownBy(() -> new Security(1L, Exchange.KOSPI, "005930", null, null, TradingStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresExchangeSymbolAndStatus() {
        assertThatThrownBy(() -> new Security(1L, null, "X", "x", null, TradingStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Security(1L, Exchange.KOSPI, null, "x", null, TradingStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Security(1L, Exchange.KOSPI, "X", "x", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void listingNormalizesBlankNames() {
        SecurityListing listing = new SecurityListing(Exchange.KOSPI, "005930", "  삼성전자 ", " ");
        assertThat(listing.nameKo()).isEqualTo("삼성전자");
        assertThat(listing.nameEn()).isNull();
        assertThat(listing.isStorable()).isTrue();
    }

    @Test
    void listingStorability() {
        assertThat(new SecurityListing(Exchange.KOSPI, "", "이름", null).isStorable())
                .isFalse();
        assertThat(new SecurityListing(Exchange.KOSPI, "A".repeat(13), "이름", null).isStorable())
                .isFalse();
        assertThat(new SecurityListing(Exchange.NASDAQ, "AAPL", null, null).isStorable())
                .isFalse();
        assertThat(new SecurityListing(Exchange.NASDAQ, "AAPL", null, "Apple").isStorable())
                .isTrue();
    }

    @Test
    void listingRequiresExchangeAndSymbol() {
        assertThatThrownBy(() -> new SecurityListing(null, "X", "x", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SecurityListing(Exchange.KOSPI, null, "x", null))
                .isInstanceOf(NullPointerException.class);
    }
}
