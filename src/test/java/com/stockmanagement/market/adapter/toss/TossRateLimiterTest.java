package com.stockmanagement.market.adapter.toss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 그룹별 간격: 초당 1회 그룹은 두 번째 호출을 약 1초 지연시킨다. 그룹끼리는 독립이다. */
class TossRateLimiterTest {

    @Test
    void spacesCallsWithinGroup() {
        TossRateLimiter limiter = new TossRateLimiter();
        long start = System.nanoTime();
        limiter.acquire(TossRateLimiter.Group.STOCK_ALL);
        limiter.acquire(TossRateLimiter.Group.STOCK_ALL);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
    }

    @Test
    void groupsAreIndependent() {
        TossRateLimiter limiter = new TossRateLimiter();
        limiter.acquire(TossRateLimiter.Group.STOCK_ALL);
        long start = System.nanoTime();
        limiter.acquire(TossRateLimiter.Group.MARKET_DATA);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
    }

    @Test
    void marketDataStaysUnderFifteenPerSecond() {
        TossRateLimiter limiter = new TossRateLimiter();
        long start = System.nanoTime();
        for (int i = 0; i < 16; i++) {
            limiter.acquire(TossRateLimiter.Group.MARKET_DATA);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .as("공시 한도(초당 15회)를 넘지 않는다")
                .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
    }
}
