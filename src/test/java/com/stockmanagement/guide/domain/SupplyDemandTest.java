package com.stockmanagement.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.market.domain.DailyCandle;
import com.stockmanagement.market.domain.InvestorFlow;
import com.stockmanagement.market.domain.ShortSellingDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplyDemandTest {

    private static final LocalDate LATEST = LocalDate.of(2026, 11, 4);

    /** 최신순 20일: 최신일 거래량 300, 나머지 19일 100. */
    private static List<DailyCandle> candles() {
        List<DailyCandle> candles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            BigDecimal volume = BigDecimal.valueOf(i == 0 ? 300 : 100);
            candles.add(new DailyCandle(LATEST.minusDays(i), null, null, null, BigDecimal.TEN, volume));
        }
        return candles;
    }

    /** 최신순 20일: 외국인 매일 +10, 기관 매일 −5, 개인은 최신일만 null(잠정치) 나머지 −5. 보유율 0.5000 → 0.5100. */
    private static List<InvestorFlow> flows() {
        List<InvestorFlow> flows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            flows.add(new InvestorFlow(
                    LATEST.minusDays(i),
                    BigDecimal.TEN,
                    BigDecimal.valueOf(-5),
                    i == 0 ? null : BigDecimal.valueOf(-5),
                    i == 0 ? new BigDecimal("0.5100") : i == 19 ? new BigDecimal("0.5000") : null));
        }
        return flows;
    }

    @Test
    void summarizesVolumeAgainstTwentyDayAverage() {
        SupplyDemand s = SupplyDemand.of(candles(), List.of(), List.of());

        assertThat(s.latestVolume()).isEqualByComparingTo("300");
        assertThat(s.averageVolume20()).isEqualByComparingTo("110");
        assertThat(s.volumeRatio()).isEqualByComparingTo("2.7273");
        assertThat(s.flows()).isNull();
        assertThat(s.shortSellingRate()).isNull();
    }

    @Test
    void sumsInvestorNetBuyingOverFiveAndTwentyDays() {
        SupplyDemand.Flows f = SupplyDemand.of(candles(), flows(), List.of()).flows();

        assertThat(f.foreign5()).isEqualByComparingTo("50");
        assertThat(f.foreign20()).isEqualByComparingTo("200");
        assertThat(f.institution20()).isEqualByComparingTo("-100");
        assertThat(f.individual5()).as("잠정치 null은 0으로 본다").isEqualByComparingTo("-20");
        assertThat(f.foreignHoldingRate()).isEqualByComparingTo("0.5100");
        assertThat(f.foreignHoldingChange()).isEqualByComparingTo("0.0100");
    }

    @Test
    void shortSellingLatestAndAverage() {
        List<ShortSellingDay> shorts = List.of(
                new ShortSellingDay(LATEST, new BigDecimal("0.06")),
                new ShortSellingDay(LATEST.minusDays(1), null),
                new ShortSellingDay(LATEST.minusDays(2), new BigDecimal("0.02")));

        SupplyDemand s = SupplyDemand.of(candles(), List.of(), shorts);

        assertThat(s.shortSellingRate()).isEqualByComparingTo("0.06");
        assertThat(s.shortSellingAverage20()).isEqualByComparingTo("0.04");
    }

    @Test
    void missingVolumesYieldEmptySummary() {
        List<DailyCandle> noVolume = List.of(new DailyCandle(LATEST, null, null, null, BigDecimal.TEN, null));
        SupplyDemand s = SupplyDemand.of(noVolume, List.of(), List.of());
        assertThat(s.latestVolume()).isNull();
        assertThat(s.volumeRatio()).isNull();
        assertThat(s.isEmpty()).isTrue();
        assertThat(SupplyDemand.of(candles(), List.of(), List.of()).isEmpty()).isFalse();
    }

    @Test
    void holdingChangeNeedsTwoRates() {
        List<InvestorFlow> one =
                List.of(new InvestorFlow(LATEST, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null));
        SupplyDemand.Flows f = SupplyDemand.of(candles(), one, List.of()).flows();
        assertThat(f.foreignHoldingRate()).isNull();
        assertThat(f.foreignHoldingChange()).isNull();
    }
}
