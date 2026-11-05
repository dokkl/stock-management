package com.stockmanagement.guide.application;

import com.stockmanagement.guide.application.port.GuideStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 3분 넘게 PENDING인 요청(서버 재시작 등으로 실행이 끊김)을 일시 장애 실패로 바꿔 슬롯을 돌려준다(research R7).
 * AI 타임아웃(60초)보다 충분히 길게 잡아 정상 실행과 겹치지 않게 한다.
 */
@Component
public class StaleGuideSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleGuideSweeper.class);
    public static final Duration STALE_AFTER = Duration.ofMinutes(3);

    private final GuideStore store;
    private final Clock clock;

    public StaleGuideSweeper(GuideStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void sweep() {
        Instant now = clock.instant();
        int failed = store.failStale(now.minus(STALE_AFTER), now);
        if (failed > 0) {
            log.warn("정체된 가이드 요청을 실패 처리: count={}", failed);
        }
    }
}
