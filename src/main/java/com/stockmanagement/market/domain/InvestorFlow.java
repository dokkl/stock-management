package com.stockmanagement.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 국내 종목 하루의 투자자별 순매수 거래량(주)과 외국인 보유 비율. 당일 잠정치에서는 개인 값과 보유 비율이 없을 수 있다.
 *
 * @param individualNet 개인 순매수. 아직 제공되지 않았으면 null
 * @param foreignHoldingRate 외국인 보유 비율(소수 비율). 아직 반영되지 않았으면 null
 */
public record InvestorFlow(
        LocalDate date,
        BigDecimal foreignNet,
        BigDecimal institutionNet,
        BigDecimal individualNet,
        BigDecimal foreignHoldingRate) {

    public InvestorFlow {
        Objects.requireNonNull(date, "date");
    }
}
