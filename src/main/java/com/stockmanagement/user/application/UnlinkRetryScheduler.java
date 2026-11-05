package com.stockmanagement.user.application;

import com.stockmanagement.user.application.port.KakaoUnlinker;
import com.stockmanagement.user.application.port.UnlinkQueue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실패한 카카오 연결 해제를 지수 백오프(1분·2분·4분… 상한 6시간)로 최대 10회 다시 시도한다(research R7). */
@Component
public class UnlinkRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnlinkRetryScheduler.class);
    public static final int MAX_ATTEMPTS = 10;
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);
    private static final int BATCH = 50;

    private final UnlinkQueue queue;
    private final KakaoUnlinker unlinker;
    private final Clock clock;

    public UnlinkRetryScheduler(UnlinkQueue queue, KakaoUnlinker unlinker, Clock clock) {
        this.queue = queue;
        this.unlinker = unlinker;
        this.clock = clock;
    }

    /** attempts번 실패한 뒤 다음 시도 시각: 2^(attempts-1)분, 최대 6시간. */
    static Instant nextAttempt(Instant now, int attempts) {
        long minutes = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration backoff = Duration.ofMinutes(minutes);
        return now.plus(backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff);
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void retry() {
        Instant now = clock.instant();
        for (UnlinkQueueEntry entry : queue.due(now, MAX_ATTEMPTS, BATCH)) {
            UnlinkResult result = unlinker.unlink(entry.kakaoId());
            if (result.unlinked()) {
                queue.remove(entry.kakaoId());
                continue;
            }
            int attempts = entry.attempts() + 1;
            queue.recordFailure(entry.kakaoId(), attempts, result.reason(), nextAttempt(now, attempts));
            if (attempts >= MAX_ATTEMPTS) {
                log.error("카카오 연결 해제 재시도 중단(최대 횟수): kakaoId={}", entry.kakaoId());
            }
        }
    }
}
