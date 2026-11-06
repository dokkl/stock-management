package com.stockmanagement.portfolio.application;

import com.stockmanagement.user.domain.UserId;
import java.util.Optional;

/** 대시보드 조회 포트(사용자 범위). 구현은 단일 쿼리로 N+1을 피한다(헌법 원칙 IV). */
public interface DashboardQuery {

    DashboardView load(UserId userId);

    Optional<DashboardView.AccountView> findAccount(UserId userId, long accountId);

    Optional<DashboardView.HoldingView> findHolding(UserId userId, long holdingId);
}
