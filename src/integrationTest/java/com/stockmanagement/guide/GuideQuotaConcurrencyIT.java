package com.stockmanagement.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.GuideRequest;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** FR-021, SC-006: 같은 종목에 동시 요청 20건을 100번 보내도 실패하지 않은 요청은 항상 2건 이하다. */
class GuideQuotaConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 20;
    private static final int ROUNDS = 100;

    @Autowired
    private GuideStore store;

    @Test
    void concurrentRequestsNeverExceedDailyLimit() throws Exception {
        Fixtures f = new Fixtures(jdbc);
        long account = f.account("키움 일반");
        long samsung = f.samsung();
        long holding = f.holding(account, samsung, "71500", "10");
        Instant now = clock.instant();
        GuideReservation reservation = new GuideReservation(
                createOwner(),
                samsung,
                holding,
                account,
                "키움 일반",
                GuideRequest.usageDateOf(now),
                AiProvider.OPENAI,
                new BigDecimal("71000"),
                new BigDecimal("71500"),
                BigDecimal.TEN,
                new BigDecimal("-0.0070"),
                Currency.KRW,
                now);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < ROUNDS; round++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<OptionalLong>> results = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return store.reserve(reservation);
                    }));
                }
                start.countDown();
                long reserved = 0;
                for (Future<OptionalLong> result : results) {
                    reserved += result.get().isPresent() ? 1 : 0;
                }

                long active = jdbc.sql("SELECT count(*) FROM guide_request WHERE status <> 'FAILED'")
                        .query(Long.class)
                        .single();
                assertThat(active).as("round %d", round).isEqualTo(2);
                assertThat(reserved).as("round %d", round).isEqualTo(2);
                jdbc.sql("DELETE FROM guide_request").update();
            }
        }
    }
}
