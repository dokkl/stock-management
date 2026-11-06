package com.stockmanagement.market.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일별 종가 관측치.
 *
 * @param previousValue 값이 있는 직전 관측치. 없으면 null
 */
public record DailyObservation(LocalDate date, BigDecimal value, BigDecimal previousValue) {}
