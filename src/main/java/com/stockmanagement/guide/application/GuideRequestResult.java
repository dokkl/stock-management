package com.stockmanagement.guide.application;

import java.time.Instant;

/** POST /holdings/{id}/guides 처리 결과(contracts/web-routes.md). */
public sealed interface GuideRequestResult {

    /** 예약했거나 진행 중인 요청을 돌려준다(202). */
    record Accepted(long guideId) implements GuideRequestResult {}

    /** 등록된 AI 키가 없다(409, 설정 화면으로 이동, FR-023d). */
    record NoCredential() implements GuideRequestResult {}

    /** 오늘 이 종목의 한도를 다 썼다(429, FR-018·FR-019). */
    record QuotaExhausted(Instant resetsAt) implements GuideRequestResult {}

    /** 거래정지·상장폐지 종목이거나 시세가 없어 요청할 수 없다(409). */
    record Unavailable(Reason reason) implements GuideRequestResult {}

    enum Reason {
        SUSPENDED,
        DELISTED,
        NO_QUOTE
    }
}
