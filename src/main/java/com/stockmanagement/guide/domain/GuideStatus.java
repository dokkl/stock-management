package com.stockmanagement.guide.domain;

/** 가이드 요청 상태. SUCCEEDED와 FAILED는 종료 상태다(FR-028). */
public enum GuideStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
