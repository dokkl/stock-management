package com.stockmanagement.market.domain;

/** 거래소. 시장을 결정한다. */
public enum Exchange {
    KOSPI(Market.KR),
    KOSDAQ(Market.KR),
    NYSE(Market.US),
    NASDAQ(Market.US),
    AMEX(Market.US);

    private final Market market;

    Exchange(Market market) {
        this.market = market;
    }

    public Market market() {
        return market;
    }
}
