package com.stockmanagement.portfolio.domain;

import com.stockmanagement.common.domain.DomainValidationException;

/** FR-006: 한 계좌에 같은 종목은 하나만. 기존 항목으로 안내하기 위해 그 id를 가진다. */
public class DuplicateHoldingException extends DomainValidationException {

    private static final long serialVersionUID = 1L;

    private final long existingHoldingId;

    public DuplicateHoldingException(long existingHoldingId) {
        super("securityId", "holding.duplicate");
        this.existingHoldingId = existingHoldingId;
    }

    public long getExistingHoldingId() {
        return existingHoldingId;
    }
}
