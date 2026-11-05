package com.stockmanagement.portfolio.application;

/** 계좌명 유니크 제약 위반(활성 계좌끼리). */
public class AccountNameTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountNameTakenException(Throwable cause) {
        super("account name taken", cause);
    }
}
