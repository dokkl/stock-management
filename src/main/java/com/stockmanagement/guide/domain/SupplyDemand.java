package com.stockmanagement.guide.domain;

import com.stockmanagement.market.domain.DailyCandle;
import com.stockmanagement.market.domain.InvestorFlow;
import com.stockmanagement.market.domain.ShortSellingDay;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 수급 요약(가이드 입력). 거래량은 국내·미국 모두, 투자자별 순매수와 공매도는 국내 종목만 있다. 값이 없으면 null이다.
 *
 * @param volumeRatio 최근 거래량 ÷ 20일 평균 거래량
 * @param flows 투자자별 순매수 요약. 국내 종목이 아니거나 데이터가 없으면 null
 * @param shortSellingRate 최근 공매도 거래량 비중(소수 비율)
 * @param shortSellingAverage20 20일 평균 공매도 비중(소수 비율)
 */
public record SupplyDemand(
        BigDecimal latestVolume,
        BigDecimal averageVolume20,
        BigDecimal volumeRatio,
        Flows flows,
        BigDecimal shortSellingRate,
        BigDecimal shortSellingAverage20) {

    private static final int WINDOW = 20;
    private static final int SHORT_WINDOW = 5;
    private static final int SCALE = 4;

    /**
     * 투자자별 순매수 거래량(주) 합계와 외국인 보유 비율.
     *
     * @param foreignHoldingChange 기간 중 가장 오래된 보유 비율 대비 변화(소수 비율)
     */
    public record Flows(
            BigDecimal foreign5,
            BigDecimal foreign20,
            BigDecimal institution5,
            BigDecimal institution20,
            BigDecimal individual5,
            BigDecimal individual20,
            BigDecimal foreignHoldingRate,
            BigDecimal foreignHoldingChange) {}

    /** 모두 최신순 목록이다. 비어 있어도 된다. */
    public static SupplyDemand of(
            List<DailyCandle> candles, List<InvestorFlow> investorFlows, List<ShortSellingDay> shortSelling) {
        List<BigDecimal> volumes = candles.stream()
                .map(DailyCandle::volume)
                .filter(Objects::nonNull)
                .limit(WINDOW)
                .toList();
        BigDecimal latest = volumes.isEmpty() ? null : volumes.getFirst();
        BigDecimal average = average(volumes);
        BigDecimal ratio =
                latest == null || average.signum() == 0 ? null : latest.divide(average, SCALE, RoundingMode.HALF_UP);
        List<BigDecimal> rates = shortSelling.stream()
                .limit(WINDOW)
                .map(ShortSellingDay::volumeRate)
                .filter(Objects::nonNull)
                .toList();
        return new SupplyDemand(
                latest,
                latest == null ? null : average,
                ratio,
                flows(investorFlows),
                shortSelling.isEmpty() ? null : shortSelling.getFirst().volumeRate(),
                rates.isEmpty() ? null : average(rates));
    }

    /** AI에 줄 수급 정보가 하나도 없다. */
    public boolean isEmpty() {
        return latestVolume == null && flows == null && shortSellingRate == null;
    }

    private static Flows flows(List<InvestorFlow> investorFlows) {
        if (investorFlows.isEmpty()) {
            return null;
        }
        List<InvestorFlow> window = investorFlows.subList(0, Math.min(WINDOW, investorFlows.size()));
        List<BigDecimal> holdings = window.stream()
                .map(InvestorFlow::foreignHoldingRate)
                .filter(Objects::nonNull)
                .toList();
        BigDecimal holding = holdings.isEmpty() ? null : holdings.getFirst();
        BigDecimal change = holdings.size() < 2 ? null : holding.subtract(holdings.getLast());
        return new Flows(
                sum(window, SHORT_WINDOW, InvestorFlow::foreignNet),
                sum(window, WINDOW, InvestorFlow::foreignNet),
                sum(window, SHORT_WINDOW, InvestorFlow::institutionNet),
                sum(window, WINDOW, InvestorFlow::institutionNet),
                sum(window, SHORT_WINDOW, InvestorFlow::individualNet),
                sum(window, WINDOW, InvestorFlow::individualNet),
                holding,
                change);
    }

    /** 최근 n일 합계. 잠정치로 비어 있는 값은 0으로 본다. */
    private static BigDecimal sum(List<InvestorFlow> flows, int n, Function<InvestorFlow, BigDecimal> value) {
        return flows.stream().limit(n).map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), SCALE + 2, RoundingMode.HALF_UP);
    }
}
