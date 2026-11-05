package com.stockmanagement.portfolio.web;

import com.stockmanagement.common.domain.DomainValidationException;
import java.math.BigDecimal;

/** 보유 종목 폼 입력값(오류 시 다시 채워 넣기 위해 원문 문자열을 유지한다). */
public record HoldingForm(Long accountId, Long securityId, String securityLabel, String avgPrice, String quantity) {

    public BigDecimal avgPriceValue() {
        return parse(avgPrice, "avgPrice");
    }

    public BigDecimal quantityValue() {
        return parse(quantity, "quantity");
    }

    /** 천 단위 구분자·공백을 허용한다. 빈 값은 null(도메인이 required로 판정). */
    static BigDecimal parse(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").strip());
        } catch (NumberFormatException e) {
            throw new DomainValidationException(field, "holding.number.invalid");
        }
    }
}
