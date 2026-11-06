package com.stockmanagement.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.market.application.port.IndicatorStore;
import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 시장 지표 저장소(003 data-model): 지표당 1행 upsert, 전일 값 없음 허용. */
class IndicatorStoreIT extends IntegrationTestBase {

    @Autowired
    private IndicatorStore store;

    private static final Instant AT = Instant.parse("2026-11-06T04:00:00Z");

    @Test
    void upsertsLatestValuePerIndicator() {
        store.save(new IndicatorQuote(
                MarketIndicator.KOSPI,
                new BigDecimal("2800.10"),
                new BigDecimal("2790"),
                AT,
                LocalDate.of(2026, 11, 6),
                AT));
        store.save(new IndicatorQuote(
                MarketIndicator.KOSPI,
                new BigDecimal("2812.45"),
                new BigDecimal("2790"),
                AT,
                LocalDate.of(2026, 11, 6),
                AT));
        store.save(new IndicatorQuote(
                MarketIndicator.VIX, new BigDecimal("15.2"), null, AT, LocalDate.of(2026, 11, 5), AT));

        assertThat(store.findAll())
                .hasSize(2)
                .anySatisfy(q -> {
                    assertThat(q.indicator()).isEqualTo(MarketIndicator.KOSPI);
                    assertThat(q.value()).isEqualByComparingTo("2812.45");
                    assertThat(q.previousClose()).isEqualByComparingTo("2790");
                    assertThat(q.asOf()).isEqualTo(AT);
                })
                .anySatisfy(q -> {
                    assertThat(q.indicator()).isEqualTo(MarketIndicator.VIX);
                    assertThat(q.previousClose()).isNull();
                    assertThat(q.asOfDate()).isEqualTo(LocalDate.of(2026, 11, 5));
                });
    }
}
