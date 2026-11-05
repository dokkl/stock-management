package com.stockmanagement.aicredential.domain;

import com.stockmanagement.common.domain.DomainValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 사용자 AI API 키 원문. 원문은 {@link #reveal()}로만 꺼낼 수 있고, toString은 끝 4자리만 보인다(FR-023b, SC-009).
 */
public final class ApiKey {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 512;
    private static final String FIELD = "apiKey";

    private final String value;

    private ApiKey(String value) {
        this.value = value;
    }

    /** 앞뒤 공백을 지우고 검증한다. 비었거나 길이가 맞지 않거나 중간에 공백이 있으면 거부한다. */
    public static ApiKey of(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            throw new DomainValidationException(FIELD, "ai.key.required");
        }
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new DomainValidationException(FIELD, "ai.key.format");
        }
        return new ApiKey(value);
    }

    /** 공급자 호출과 암호화에만 쓴다. 로그·화면에 넘기지 않는다. */
    public String reveal() {
        return value;
    }

    public String last4() {
        return value.substring(value.length() - 4);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiKey key
                && MessageDigest.isEqual(
                        value.getBytes(StandardCharsets.UTF_8), key.value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "****" + last4();
    }
}
