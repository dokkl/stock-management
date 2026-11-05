package com.stockmanagement.portfolio.application;

/** 활성 계좌에 속한 보유 종목의 식별 정보. */
public record HoldingRef(long id, long accountId, long securityId) {}
