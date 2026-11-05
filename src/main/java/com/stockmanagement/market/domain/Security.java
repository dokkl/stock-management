package com.stockmanagement.market.domain;

import com.stockmanagement.common.domain.Currency;
import java.util.Objects;

/** 상장 종목. 시장·통화는 거래소에서 파생된다. */
public record Security(
        Long id, Exchange exchange, String symbol, String nameKo, String nameEn, TradingStatus tradingStatus) {

    public Security {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(tradingStatus, "tradingStatus");
        if (nameKo == null && nameEn == null) {
            throw new IllegalArgumentException("종목명이 없습니다: " + symbol);
        }
    }

    public Market market() {
        return exchange.market();
    }

    public Currency currency() {
        return market().currency();
    }

    /** 화면 표시 이름: 한글명 우선, 없으면 영문명. */
    public String displayName() {
        return nameKo != null && !nameKo.isBlank() ? nameKo : nameEn;
    }
}
