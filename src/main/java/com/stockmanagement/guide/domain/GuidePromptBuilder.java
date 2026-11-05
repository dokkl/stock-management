package com.stockmanagement.guide.domain;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.portfolio.domain.HoldingValuation;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 최근 일봉과 보유 정보로 GuidePrompt를 만든다(FR-016). 부동소수점 없이 BigDecimal로 계산한다. */
public final class GuidePromptBuilder {

    public static final int LOOKBACK_DAYS = 60;
    private static final int SCALE = 4;
    private static final int RETURN_SCALE = 10;
    private static final MathContext SQRT = new MathContext(12);

    private GuidePromptBuilder() {}

    /** 종목 식별 정보. */
    public record Stock(Market market, String symbol, String name, Currency currency) {}

    /**
     * 분석 입력을 만든다.
     *
     * @param closesNewestFirst 일봉 종가(최신순). 앞에서부터 최대 60개를 쓴다
     */
    public static GuidePrompt build(
            Stock stock,
            BigDecimal price,
            BigDecimal prevClose,
            List<BigDecimal> closesNewestFirst,
            BigDecimal avgPrice,
            BigDecimal quantity) {
        if (closesNewestFirst.isEmpty()) {
            throw new IllegalArgumentException("일봉 종가가 없습니다");
        }
        List<BigDecimal> closes =
                new ArrayList<>(closesNewestFirst.subList(0, Math.min(LOOKBACK_DAYS, closesNewestFirst.size())));
        Collections.reverse(closes);
        HoldingValuation valuation = HoldingValuation.of(avgPrice, quantity, price, stock.currency());
        return new GuidePrompt(
                stock.market(),
                stock.symbol(),
                stock.name(),
                stock.currency(),
                price,
                prevClose,
                closes,
                average(closes, 20),
                average(closes, LOOKBACK_DAYS),
                Collections.max(closes),
                Collections.min(closes),
                volatility(closes),
                avgPrice,
                quantity,
                valuation.unrealizedPnl().amount(),
                valuation.returnRate());
    }

    /** 최근 n개(부족하면 있는 만큼) 종가의 평균. closes는 오래된 순이다. */
    private static BigDecimal average(List<BigDecimal> closes, int n) {
        List<BigDecimal> window = closes.subList(Math.max(0, closes.size() - n), closes.size());
        BigDecimal sum = window.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(window.size()), SCALE, RoundingMode.HALF_UP);
    }

    /** 일간 수익률의 표본 표준편차. 수익률이 2개 미만이면 0. */
    private static BigDecimal volatility(List<BigDecimal> closes) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            BigDecimal previous = closes.get(i - 1);
            if (previous.signum() != 0) {
                returns.add(closes.get(i)
                        .divide(previous, RETURN_SCALE, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE));
            }
        }
        if (returns.size() < 2) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        BigDecimal n = BigDecimal.valueOf(returns.size());
        BigDecimal mean =
                returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(n, RETURN_SCALE, RoundingMode.HALF_UP);
        BigDecimal squares =
                returns.stream().map(r -> r.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = squares.divide(n.subtract(BigDecimal.ONE), RETURN_SCALE, RoundingMode.HALF_UP);
        return variance.sqrt(SQRT).setScale(SCALE + 2, RoundingMode.HALF_UP);
    }
}
