package com.stockmanagement.market.domain;

/** 대시보드 시장 지표(003 data-model). 선언 순서가 화면 순서다. 제공처 키는 어댑터가 가진다. */
public enum MarketIndicator {
    KOSPI(Kind.REALTIME, Unit.POINT),
    KOSDAQ(Kind.REALTIME, Unit.POINT),
    USD_KRW(Kind.REALTIME, Unit.KRW),
    NASDAQ(Kind.DAILY_CLOSE, Unit.POINT),
    SP500(Kind.DAILY_CLOSE, Unit.POINT),
    DOW(Kind.DAILY_CLOSE, Unit.POINT),
    WTI(Kind.DAILY_CLOSE, Unit.USD_PER_BARREL),
    VIX(Kind.DAILY_CLOSE, Unit.POINT);

    /** 갱신 방식: 국내 장중 실시간 또는 일별 종가. */
    public enum Kind {
        REALTIME,
        DAILY_CLOSE
    }

    /** 표시 단위. */
    public enum Unit {
        POINT,
        KRW,
        USD_PER_BARREL
    }

    private final Kind kind;
    private final Unit unit;

    MarketIndicator(Kind kind, Unit unit) {
        this.kind = kind;
        this.unit = unit;
    }

    public Kind kind() {
        return kind;
    }

    public Unit unit() {
        return unit;
    }

    public boolean isRealtime() {
        return kind == Kind.REALTIME;
    }
}
