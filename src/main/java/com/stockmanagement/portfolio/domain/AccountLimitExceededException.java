package com.stockmanagement.portfolio.domain;

import com.stockmanagement.common.domain.DomainValidationException;

/** 계좌 상한을 넘어 만들려고 했다(FR-002a). 폼의 계좌명 필드 오류로 보여 준다. */
public class AccountLimitExceededException extends DomainValidationException {

    private static final long serialVersionUID = 1L;

    private final int maxAccounts;

    public AccountLimitExceededException(int maxAccounts) {
        super("name", "account.limit");
        this.maxAccounts = maxAccounts;
    }

    public int getMaxAccounts() {
        return maxAccounts;
    }
}
