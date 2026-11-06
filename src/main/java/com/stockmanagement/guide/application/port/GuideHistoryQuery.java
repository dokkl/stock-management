package com.stockmanagement.guide.application.port;

import com.stockmanagement.guide.application.GuideHistoryPage;
import com.stockmanagement.user.domain.UserId;
import java.util.Optional;

/** 사용자의 종목별 성공 가이드 기록 조회(FR-026). 종목이 없으면 비어 있다. 종목은 공유, 기록은 사용자 것만. */
public interface GuideHistoryQuery {

    Optional<GuideHistoryPage> find(UserId userId, long securityId, int page);
}
