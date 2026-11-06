package com.stockmanagement.user.domain;

/** 화면 표시 이름. 카카오 닉네임(선택 동의)이 없으면 기본 이름을 쓴다(FR-004). */
public record DisplayName(String value) {

    public static final String DEFAULT = "카카오 사용자";
    public static final int MAX_LENGTH = 40;

    public DisplayName {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("표시 이름은 1~40자입니다");
        }
    }

    /** 앞뒤 공백을 지우고 40자로 자른다. 비어 있으면 기본 이름. */
    public static DisplayName of(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.isEmpty()) {
            return new DisplayName(DEFAULT);
        }
        int end = trimmed.offsetByCodePoints(0, Math.min(trimmed.codePointCount(0, trimmed.length()), MAX_LENGTH));
        return new DisplayName(trimmed.substring(0, end).strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
