package com.stockmanagement.guide.application.port;

import com.stockmanagement.guide.application.GuideHistoryPage;
import java.util.Optional;

/** 종목별 성공 가이드 기록 조회(FR-026). 종목이 없으면 비어 있다. */
public interface GuideHistoryQuery {

    Optional<GuideHistoryPage> find(long securityId, int page);
}
