package com.stockmanagement.portfolio.domain;

/** FR-002a: 소유자 1인의 활성 계좌 상한(기본 2). 삭제된 계좌는 세지 않는다. */
public record AccountLimitPolicy(int maxAccounts) {

    public AccountLimitPolicy {
        if (maxAccounts <= 0) {
            throw new IllegalArgumentException("maxAccounts must be > 0");
        }
    }

    public void checkCanAdd(long currentAccounts) {
        if (currentAccounts >= maxAccounts) {
            throw new AccountLimitExceededException(maxAccounts);
        }
    }
}
