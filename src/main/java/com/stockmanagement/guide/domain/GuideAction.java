package com.stockmanagement.guide.domain;

/** 행동 가이드 분류(contracts/ai-guide-output.schema.json). */
public enum GuideAction {
    HOLD,
    BUY_MORE,
    PARTIAL_SELL,
    SELL,
    WATCH
}
