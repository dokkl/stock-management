package com.stockmanagement.portfolio.domain;

import com.stockmanagement.common.domain.DomainValidationException;

public class HoldingLimitExceededException extends DomainValidationException {

    private static final long serialVersionUID = 1L;

    private final int maxHoldings;

    public HoldingLimitExceededException(int maxHoldings) {
        super("securityId", "holding.limit");
        this.maxHoldings = maxHoldings;
    }

    public int getMaxHoldings() {
        return maxHoldings;
    }
}
