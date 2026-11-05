package com.stockmanagement.portfolio.domain;

/** FR-003a: 소유자 전체 보유 종목 상한(기본 500). 헌법 원칙 IV 예외의 근거(plan.md Complexity Tracking). */
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
