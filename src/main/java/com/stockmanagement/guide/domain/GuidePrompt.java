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
 * @param supplyDemand 수급 요약. 없으면 null
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
        BigDecimal returnRate,
        SupplyDemand supplyDemand) {

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
                "- 평가손익: " + c + plain(unrealizedPnl) + ", 수익률: " + percent(returnRate),
                supplyDemandSection());
    }

    /** [수급] 항목. 거래량은 국내·미국 모두, 투자자별 순매수·공매도는 국내 종목만 있다. */
    private String supplyDemandSection() {
        if (supplyDemand == null || supplyDemand.isEmpty()) {
            return "\n[수급]\n- 수급 데이터 없음";
        }
        StringBuilder text = new StringBuilder("\n[수급]");
        SupplyDemand s = supplyDemand;
        if (s.latestVolume() != null) {
            text.append("\n- 최근 거래량: ")
                    .append(plain(s.latestVolume()))
                    .append("주, 20일 평균: ")
                    .append(plain(s.averageVolume20().setScale(0, RoundingMode.HALF_UP)))
                    .append("주");
            if (s.volumeRatio() != null) {
                text.append(" (평균 대비 ")
                        .append(plain(s.volumeRatio().setScale(2, RoundingMode.HALF_UP)))
                        .append("배)");
            }
        }
        if (s.flows() != null) {
            SupplyDemand.Flows f = s.flows();
            text.append("\n- 투자자별 순매수(주, 최근 5일 / 20일 합계): 외국인 ")
                    .append(plain(f.foreign5()))
                    .append(" / ")
                    .append(plain(f.foreign20()))
                    .append(", 기관 ")
                    .append(plain(f.institution5()))
                    .append(" / ")
                    .append(plain(f.institution20()))
                    .append(", 개인 ")
                    .append(plain(f.individual5()))
                    .append(" / ")
                    .append(plain(f.individual20()));
            if (f.foreignHoldingRate() != null) {
                text.append("\n- 외국인 보유 비율: ").append(percent(f.foreignHoldingRate()));
                if (f.foreignHoldingChange() != null) {
                    text.append(" (기간 중 변화 ")
                            .append(signedPercent(f.foreignHoldingChange()))
                            .append(")");
                }
            }
        }
        if (s.shortSellingRate() != null) {
            text.append("\n- 공매도 거래량 비중: 최근 ").append(percent(s.shortSellingRate()));
            if (s.shortSellingAverage20() != null) {
                text.append(", 20일 평균 ").append(percent(s.shortSellingAverage20()));
            }
        }
        return text.toString();
    }

    private static String signedPercent(BigDecimal ratio) {
        return (ratio.signum() > 0 ? "+" : "") + percent(ratio);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String percent(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
