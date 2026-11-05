package com.stockmanagement.market.domain;

import java.time.Duration;
import java.time.Instant;

/** 화면에 표시할 시세 상태(FR-012~FR-014). */
public enum QuoteStatus {
    /** 장중이고 마지막 수신이 기준 시간 이내. */
    LIVE,
    /** 장중인데 마지막 수신이 기준 시간보다 오래됨. 마지막 값은 유지한다. */
    STALE,
    /** 장 시작 전. 직전 거래일 값을 보여준다. */
    PRE_OPEN,
    /** 장 마감 또는 휴장. 종가를 보여준다. */
    CLOSED,
    /** 시세 행이 없다. */
    UNAVAILABLE;

    public static QuoteStatus of(Quote quote, Instant now, Duration staleAfter) {
        return quote == null ? UNAVAILABLE : quote.status(now, staleAfter);
    }
}
