package com.stockmanagement.market.domain;

/** 지표 카드의 기준 시각 표시 상태(003 FR-006, FR-007). */
public enum IndicatorStatus {
    /** 국내 장중, 마지막 수신이 기준 시간 이내. */
    LIVE,
    /** 국내 장중인데 마지막 수신이 오래됨("지연"). */
    STALE,
    /** 국내 장 외(장 전·마감·휴장): 마지막 값. */
    CLOSED,
    /** 미국 지표: 일별 종가. */
    DAILY_CLOSE,
    /** 값을 받은 적 없음. */
    UNAVAILABLE
}
