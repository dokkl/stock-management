package com.stockmanagement.portfolio.domain;

import com.stockmanagement.common.domain.DomainValidationException;

/** 계좌명: 앞뒤 공백 제거 후 1~30자(FR-002). */
public record AccountName(String value) {

    public static final int MAX_LENGTH = 30;

    public AccountName {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("name", "account.name.required");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new DomainValidationException("name", "account.name.tooLong");
        }
    }

    public static AccountName of(String raw) {
        return new AccountName(raw == null ? null : raw.strip());
    }
}
