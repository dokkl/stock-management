package com.stockmanagement.portfolio.application;

import java.util.Optional;

/** 대시보드 조회 포트. 구현은 단일 쿼리로 N+1을 피한다(헌법 원칙 IV). */
public interface DashboardQuery {

    DashboardView load();

    Optional<DashboardView.AccountView> findAccount(long accountId);

    Optional<DashboardView.HoldingView> findHolding(long holdingId);
}
