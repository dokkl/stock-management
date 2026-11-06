package com.stockmanagement.aicredential.domain;

/** 공급자 키 검증 결과(FR-023a, FR-023e). */
public enum KeyCheck {
    /** 공급자가 키를 받아들였다. */
    VALID,
    /** 인증 실패(401/403). 저장하지 않는다. */
    INVALID,
    /** 시간 초과·서버 오류 등 일시 장애로 확인하지 못했다. */
    UNAVAILABLE
}
