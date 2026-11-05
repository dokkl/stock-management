package com.stockmanagement.portfolio.application;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.domain.GuideQuota;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Quote;
import com.stockmanagement.market.domain.QuoteStatus;
import com.stockmanagement.market.domain.TradingStatus;
import com.stockmanagement.portfolio.domain.HoldingValuation;
import java.math.BigDecimal;
import java.util.List;

/** 대시보드 화면 데이터: 계좌 목록 → 보유 종목 행. */
public record DashboardView(List<AccountView> accounts) {

    public int totalHoldings() {
        return accounts.stream().mapToInt(a -> a.holdings().size()).sum();
    }

    public boolean isEmpty() {
        return accounts.isEmpty();
    }

    public record AccountView(long id, String name, List<HoldingView> holdings) {}

    /**
     * 보유 종목 행.
     *
     * @param quote 저장된 시세. 없으면 null
     * @param quoteStatus 조회 시점 기준 시세 상태
     * @param guidesUsedToday 오늘(KST) 이 종목의 실패하지 않은 가이드 요청 수(계좌 합산, FR-018)
     */
    public record HoldingView(
            long id,
            long accountId,
            long securityId,
            Exchange exchange,
            String symbol,
            String name,
            Currency currency,
            TradingStatus tradingStatus,
            BigDecimal avgPrice,
            BigDecimal quantity,
            Quote quote,
            QuoteStatus quoteStatus,
            int guidesUsedToday) {

        public GuideQuota guideQuota() {
            return new GuideQuota(guidesUsedToday);
        }

        /** 가이드를 요청할 수 없는 상태: 한도 소진 또는 거래정지·상장폐지. */
        public boolean guideDisabled() {
            return guideQuota().isExhausted() || tradingStatus != TradingStatus.ACTIVE;
        }

        public boolean hasQuote() {
            return quote != null;
        }

        public boolean isSuspended() {
            return tradingStatus == TradingStatus.SUSPENDED;
        }

        /** 평가손익. 시세가 없으면 null. */
        public HoldingValuation valuation() {
            return quote == null ? null : HoldingValuation.of(avgPrice, quantity, quote.price(), currency);
        }
    }
}
