package com.stockmanagement.portfolio.application;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.TradingStatus;
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
            BigDecimal quantity) {}
}
