package com.stockmanagement.market.adapter.toss;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 토스증권 Rate Limits Group별 초당 한도에 맞춰 호출 간격을 둔다(overview.md "Rate Limits").
 * 공지 없이 조정될 수 있어 공시 한도보다 약간 낮게 잡는다.
 */
public final class TossRateLimiter {

    /** 사용하는 그룹과 초당 허용 호출 수. */
    public enum Group {
        AUTH(4),
        STOCK_ALL(1),
        /** 공시 한도를 확인하지 못해 MARKET_INFO와 같은 수준으로 보수적으로 잡는다. */
        STOCK(2),
        MARKET_INFO(2),
        MARKET_DATA(12),
        MARKET_DATA_CHART(15);

        private final int perSecond;

        Group(int perSecond) {
            this.perSecond = perSecond;
        }
    }

    private final Map<Group, long[]> nextAllowed = new EnumMap<>(Group.class);

    public TossRateLimiter() {
        for (Group group : Group.values()) {
            nextAllowed.put(group, new long[] {System.nanoTime()});
        }
    }

    /** 그룹의 다음 호출 슬롯까지 대기한다. */
    public void acquire(Group group) {
        long interval = TimeUnit.SECONDS.toNanos(1) / group.perSecond;
        long[] slot = nextAllowed.get(group);
        long waitNanos;
        synchronized (slot) {
            long now = System.nanoTime();
            long allowed = Math.max(now, slot[0]);
            slot[0] = allowed + interval;
            waitNanos = allowed - now;
        }
        if (waitNanos > 0) {
            sleep(waitNanos);
        }
    }

    private static void sleep(long nanos) {
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토스증권 API 호출 대기 중 인터럽트", e);
        }
    }
}
