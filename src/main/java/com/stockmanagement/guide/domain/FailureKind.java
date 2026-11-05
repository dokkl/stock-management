package com.stockmanagement.guide.domain;

/** 가이드 실패 사유(FR-023e). 실패한 요청은 사용 횟수에서 빠진다(FR-020). */
public enum FailureKind {
    /** 키 인증 실패·잔액 부족: 사용자가 키를 확인해야 한다. */
    CREDENTIAL_ERROR,
    /** 시간 초과·서버 오류·처리 중단: 잠시 후 다시 시도할 수 있다. */
    TRANSIENT_ERROR,
    /** 실행 시점에 종목이 거래정지·상장폐지 상태였다. */
    INVALID_STATE
}
