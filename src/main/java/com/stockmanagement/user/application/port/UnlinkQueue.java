package com.stockmanagement.user.application.port;

import com.stockmanagement.user.application.UnlinkQueueEntry;
import com.stockmanagement.user.domain.KakaoId;
import java.time.Instant;
import java.util.List;

/** 연결 해제 재시도 대기열(kakao_unlink_queue). */
public interface UnlinkQueue {

    /** 처음 실패한 연결 해제를 넣는다(이미 있으면 그대로 둔다). */
    void enqueue(KakaoId kakaoId, String reason, Instant nextAttemptAt);

    /** 지금 시도할 차례이고 최대 시도 횟수 전인 항목. */
    List<UnlinkQueueEntry> due(Instant now, int maxAttempts, int limit);

    void recordFailure(KakaoId kakaoId, int attempts, String reason, Instant nextAttemptAt);

    void remove(KakaoId kakaoId);
}
