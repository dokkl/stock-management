package com.stockmanagement.market.domain;

import com.stockmanagement.common.domain.Currency;

/** 시장. 통화는 시장이 결정한다(불변식: KR ⇔ KRW). */
public enum Market {
    KR(Currency.KRW),
    US(Currency.USD);

    private final Currency currency;

    Market(Currency currency) {
        this.currency = currency;
    }

    public Currency currency() {
        return currency;
    }
}
