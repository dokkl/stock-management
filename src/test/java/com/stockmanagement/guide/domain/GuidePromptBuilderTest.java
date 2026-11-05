package com.stockmanagement.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.market.domain.Market;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuidePromptBuilderTest {

    private static final GuidePromptBuilder.Stock SAMSUNG =
            new GuidePromptBuilder.Stock(Market.KR, "005930", "삼성전자", Currency.KRW);

    /** 최신순 종가: 159, 158, …, 100 (60거래일). */
    private static List<BigDecimal> closesNewestFirst() {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 159; i >= 100; i--) {
            closes.add(BigDecimal.valueOf(i));
        }
        return closes;
    }

    private static GuidePrompt build(List<BigDecimal> closes) {
        return GuidePromptBuilder.build(
                SAMSUNG,
                new BigDecimal("160"),
                new BigDecimal("159"),
                closes,
                new BigDecimal("150"),
                new BigDecimal("10"));
    }

    @Test
    void computesMovingAveragesAndRange() {
        GuidePrompt prompt = build(closesNewestFirst());

        assertThat(prompt.closes()).hasSize(60).first().isEqualTo(BigDecimal.valueOf(100));
        assertThat(prompt.ma20()).isEqualByComparingTo("149.5");
        assertThat(prompt.ma60()).isEqualByComparingTo("129.5");
        assertThat(prompt.high()).isEqualByComparingTo("159");
        assertThat(prompt.low()).isEqualByComparingTo("100");
    }

    @Test
    void usesOnlyLatestSixtyCloses() {
        List<BigDecimal> closes = new ArrayList<>(closesNewestFirst());
        closes.add(BigDecimal.ONE);
        assertThat(build(closes).closes()).hasSize(60);
        assertThat(build(closes).low()).isEqualByComparingTo("100");
    }

    @Test
    void volatilityIsSampleStdDevOfDailyReturns() {
        GuidePrompt prompt = build(List.of(new BigDecimal("99"), new BigDecimal("110"), new BigDecimal("100")));
        // 수익률 +10%, −10% → 표본 표준편차 0.1414…
        assertThat(prompt.volatility()).isBetween(new BigDecimal("0.1414"), new BigDecimal("0.1415"));
        assertThat(build(List.of(BigDecimal.TEN)).volatility()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsMissingClosesAndSkipsZeroPrices() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> build(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        GuidePrompt prompt =
                build(List.of(new BigDecimal("110"), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("50")));
        assertThat(prompt.volatility()).as("0원 다음 수익률은 계산하지 않는다").isNotNull();
    }

    @Test
    void describesUsMarket() {
        GuidePrompt prompt = GuidePromptBuilder.build(
                new GuidePromptBuilder.Stock(Market.US, "AAPL", "애플", Currency.USD),
                new BigDecimal("187.50"),
                new BigDecimal("190"),
                List.of(new BigDecimal("190")),
                new BigDecimal("185.2"),
                new BigDecimal("0.35"));
        assertThat(prompt.userMessage()).contains("미국", "$187.5", "USD");
    }

    @Test
    void includesHoldingSituation() {
        GuidePrompt prompt = build(closesNewestFirst());

        assertThat(prompt.avgPrice()).isEqualByComparingTo("150");
        assertThat(prompt.quantity()).isEqualByComparingTo("10");
        assertThat(prompt.unrealizedPnl()).isEqualByComparingTo("100");
        assertThat(prompt.returnRate()).isEqualByComparingTo("0.0667");
        assertThat(prompt.userMessage()).contains("삼성전자", "005930", "₩", "평단가", "수량", "평가손익", "20일 이동평균", "변동성");
    }

    @Test
    void neverIncludesAccountName() {
        GuidePrompt prompt = build(closesNewestFirst());
        assertThat(prompt.userMessage()).doesNotContain("계좌").doesNotContain("키움");
    }
}
