package com.stockmanagement.common.domain;

/** 도메인 규칙 위반. field는 폼 필드 이름, messageKey는 messages.properties 키다. */
public class DomainValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final String messageKey;

    public DomainValidationException(String field, String messageKey) {
        super(field + ": " + messageKey);
        this.field = field;
        this.messageKey = messageKey;
    }

    public String getField() {
        return field;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
