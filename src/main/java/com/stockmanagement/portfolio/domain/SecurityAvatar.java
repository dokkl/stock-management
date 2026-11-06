package com.stockmanagement.portfolio.domain;

import java.util.Locale;

/**
 * 종목 글자 아이콘: 종목명 첫 글자와 종목 코드로 정해지는 색조(1~{@value #TONES}).
 *
 * <p>같은 종목은 항상 같은 색이다. 색조 팔레트는 등락 색(빨강·파랑)과 겹치지 않게 tokens.css에 둔다.
 */
public record SecurityAvatar(String initial, int tone) {

    public static final int TONES = 8;

    public static SecurityAvatar of(String symbol, String name) {
        String source = name == null || name.isBlank() ? symbol : name.strip();
        String first = new String(Character.toChars(source.codePointAt(0)));
        return new SecurityAvatar(first.toUpperCase(Locale.ROOT), Math.floorMod(symbol.hashCode(), TONES) + 1);
    }
}
