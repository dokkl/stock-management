package com.stockmanagement.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** 일봉 1개. 금액은 종목 통화 기준이다. */
public record DailyCandle(
        LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {

    public DailyCandle {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
    }
}
