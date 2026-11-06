package com.stockmanagement.market.application;

import java.math.BigDecimal;
import java.time.Instant;

/** 제공처가 알려 준 지표 현재 값과 그 기준 시각. */
public record IndicatorReading(BigDecimal value, Instant asOf) {}
