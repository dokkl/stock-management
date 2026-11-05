package com.stockmanagement.market.domain;

import java.util.Objects;

/** 종목 마스터 파일의 한 항목. */
public record SecurityListing(Exchange exchange, String symbol, String nameKo, String nameEn) {

    public static final int MAX_SYMBOL_LENGTH = 12;

    public SecurityListing {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        nameKo = blankToNull(nameKo);
        nameEn = blankToNull(nameEn);
    }

    /** 저장 가능한 항목인지: 심볼 길이와 이름 존재. */
    public boolean isStorable() {
        return !symbol.isBlank() && symbol.length() <= MAX_SYMBOL_LENGTH && (nameKo != null || nameEn != null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
