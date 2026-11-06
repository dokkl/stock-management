package com.stockmanagement.market.application.port;

import com.stockmanagement.market.application.IndicatorReading;
import com.stockmanagement.market.domain.MarketIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** 국내 지수(코스피·코스닥)와 원·달러 환율 제공처. */
public interface DomesticIndicatorSource {

    /** 코스피·코스닥 현재 값. 값이 없는 지수는 빠진다. */
    Map<MarketIndicator, IndicatorReading> currentIndices();

    /** sessionDate 이전 마지막 거래일의 지수 종가. */
    Optional<BigDecimal> previousIndexClose(MarketIndicator index, LocalDate sessionDate);

    /** 원·달러 매매기준율 현재 값. */
    Optional<IndicatorReading> currentUsdKrw();

    /** 주어진 시각의 원·달러 매매기준율(전일 비교용). */
    Optional<BigDecimal> usdKrwAt(Instant at);
}
