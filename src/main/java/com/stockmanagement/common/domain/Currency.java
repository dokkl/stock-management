package com.stockmanagement.common.domain;

/** 거래 통화. 통화별 평단가 최대 소수 자릿수와 현재가 표시 자릿수를 가진다. */
public enum Currency {
    KRW("₩", 0, 0),
    USD("$", 4, 2);

    private final String symbol;
    private final int maxScale;
    private final int displayScale;

    Currency(String symbol, int maxScale, int displayScale) {
        this.symbol = symbol;
        this.maxScale = maxScale;
        this.displayScale = displayScale;
    }

    public String symbol() {
        return symbol;
    }

    /** 평단가 등 입력 금액에 허용되는 최대 소수 자릿수. */
    public int maxScale() {
        return maxScale;
    }

    /** 현재가·손익 표시 소수 자릿수. */
    public int displayScale() {
        return displayScale;
    }
}
