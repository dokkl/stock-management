package com.stockmanagement.guide.domain;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.market.domain.Market;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI에 보내는 분석 입력(contracts/ports.md AiGuideProvider). 종목·시세·최근 60거래일 요약·보유 정보만 담고
 * 계좌명 같은 개인 식별 정보는 담지 않는다.
 *
 * @param closes 최근 종가(오래된 것부터)
 * @param volatility 일간 수익률의 표본 표준편차(비율)
 * @param returnRate 평가 수익률(비율)
 */
public record GuidePrompt(
        Market market,
        String symbol,
        String name,
        Currency currency,
        BigDecimal price,
        BigDecimal prevClose,
        List<BigDecimal> closes,
        BigDecimal ma20,
        BigDecimal ma60,
        BigDecimal high,
        BigDecimal low,
        BigDecimal volatility,
        BigDecimal avgPrice,
        BigDecimal quantity,
        BigDecimal unrealizedPnl,
        BigDecimal returnRate) {

    public GuidePrompt {
        closes = List.copyOf(closes);
    }

    /** 사용자 메시지 본문(한국어). 금액은 통화 기호와 함께 쓴다. */
    public String userMessage() {
        String c = currency.symbol();
        return String.join(
                "\n",
                "다음 종목과 내 보유 상황을 분석해 주세요.",
                "",
                "[종목]",
                "- 시장: " + (market == Market.KR ? "한국" : "미국") + ", 종목: " + name + " (" + symbol + "), 통화: "
                        + currency.name(),
                "- 현재가: " + c + plain(price) + ", 전일 종가: " + c + plain(prevClose),
                "",
                "[최근 " + closes.size() + "거래일 종가 요약]",
                "- 종가(오래된 순): " + closes.stream().map(GuidePrompt::plain).collect(Collectors.joining(", ")),
                "- 20일 이동평균: " + c + plain(ma20) + ", 60일 이동평균: " + c + plain(ma60),
                "- 기간 최고: " + c + plain(high) + ", 기간 최저: " + c + plain(low),
                "- 일간 수익률 변동성(표준편차): " + percent(volatility),
                "",
                "[내 보유 상황]",
                "- 평단가: " + c + plain(avgPrice) + ", 수량: " + plain(quantity) + "주",
                "- 평가손익: " + c + plain(unrealizedPnl) + ", 수익률: " + percent(returnRate));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String percent(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
