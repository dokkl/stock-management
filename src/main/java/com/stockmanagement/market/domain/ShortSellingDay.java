package com.stockmanagement.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 국내 종목 하루의 공매도 거래량 비중.
 *
 * @param volumeRate 전체 거래량 대비 공매도 거래량(소수 비율). 분모가 없으면 null
 */
public record ShortSellingDay(LocalDate date, BigDecimal volumeRate) {

    public ShortSellingDay {
        Objects.requireNonNull(date, "date");
    }
}
