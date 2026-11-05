package com.stockmanagement.market.domain;

import com.stockmanagement.common.domain.Currency;
import java.time.ZoneId;

/** 시장. 통화는 시장이 결정한다(불변식: KR ⇔ KRW). */
public enum Market {
    KR(Currency.KRW, ZoneId.of("Asia/Seoul")),
    US(Currency.USD, ZoneId.of("America/New_York"));

    private final Currency currency;
    private final ZoneId zone;

    Market(Currency currency, ZoneId zone) {
        this.currency = currency;
        this.zone = zone;
    }

    /** 시장 현지 시간대. 장 운영일의 "오늘"은 이 시간대 기준이다. */
    public ZoneId zone() {
        return zone;
    }

    public Currency currency() {
        return currency;
    }
}
