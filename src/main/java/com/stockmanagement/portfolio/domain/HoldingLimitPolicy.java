package com.stockmanagement.portfolio.domain;

/** FR-003a: 소유자 전체 보유 종목 상한(기본 20). 대시보드가 페이지 없이 전체를 보여 줄 수 있는 근거다. */
public record HoldingLimitPolicy(int maxHoldings) {

    public HoldingLimitPolicy {
        if (maxHoldings <= 0) {
            throw new IllegalArgumentException("maxHoldings must be > 0");
        }
    }

    public void checkCanAdd(long currentHoldings) {
        if (currentHoldings >= maxHoldings) {
            throw new HoldingLimitExceededException(maxHoldings);
        }
    }
}
