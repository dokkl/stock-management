package com.stockmanagement.common.web;

import com.stockmanagement.common.domain.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** 숫자·통화·등락 표기의 단일 포매터(헌법 원칙 III). Thymeleaf에서는 {@code #money}로 쓴다. */
public final class MoneyFormatter {

    private static final String UP = "▲";
    private static final String DOWN = "▼";
    private static final String FLAT = "–";
    private static final String MINUS = "−";

    /** 현재가·손익: 통화 표시 자릿수(KRW 0, USD 2). */
    public String price(BigDecimal amount, Currency currency) {
        String sign = amount.signum() < 0 ? MINUS : "";
        return sign + currency.symbol() + number(amount.abs(), currency.displayScale());
    }

    /** 평단가: 통화 최대 자릿수(KRW 0, USD 4). */
    public String avgPrice(BigDecimal amount, Currency currency) {
        return currency.symbol() + number(amount, currency.maxScale());
    }

    /** 등락: "▲ 1,000 (+1.43%)", "▼ 2.50 (−1.32%)", "– 0 (0.00%)". rate는 비율(0.0143 = 1.43%). */
    public String change(BigDecimal change, BigDecimal rate, Currency currency) {
        String arrow = arrow(change);
        String amount = number(change.abs(), currency.displayScale());
        return arrow + " " + amount + " (" + percent(rate) + ")";
    }

    /** 비율을 부호 있는 퍼센트로: +1.43%, −1.32%, 0.00%. */
    public String percent(BigDecimal rate) {
        BigDecimal pct = rate.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
        String sign = switch (pct.signum()) {
            case 1 -> "+";
            case -1 -> MINUS;
            default -> "";
        };
        return sign + number(pct.abs(), 2) + "%";
    }

    public String arrow(BigDecimal value) {
        return switch (value.signum()) {
            case 1 -> UP;
            case -1 -> DOWN;
            default -> FLAT;
        };
    }

    /** 수량: 불필요한 0 제거 + "주". */
    public String quantity(BigDecimal quantity) {
        BigDecimal stripped = quantity.stripTrailingZeros();
        int scale = Math.max(stripped.scale(), 0);
        return number(stripped, scale) + "주";
    }

    /** 입력란에 넣을 값: 불필요한 0 제거, 천 단위 구분자 없음. */
    public String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String number(BigDecimal value, int scale) {
        DecimalFormat format = new DecimalFormat(pattern(scale), DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value.setScale(scale, RoundingMode.HALF_UP));
    }

    private static String pattern(int scale) {
        return scale == 0 ? "#,##0" : "#,##0." + "0".repeat(scale);
    }
}
