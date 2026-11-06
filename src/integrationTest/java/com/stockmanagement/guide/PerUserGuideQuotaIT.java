package com.stockmanagement.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.GuideService;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.GuideRequest;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.user.domain.UserId;
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

/** 사용자별 하루 가이드 한도(T034, FR-014, SC-005): 같은 종목이어도 사용자마다 2회, 동시 요청에도 지킨다. */
class PerUserGuideQuotaIT extends IntegrationTestBase {

    @Autowired
    private GuideStore store;

    @Autowired
    private GuideService guideService;

    private GuideReservation reservation(UserId user, long security, long holding, long account, Instant at) {
        return new GuideReservation(
                user,
                security,
                holding,
                account,
                "계좌",
                GuideRequest.usageDateOf(at),
                AiProvider.OPENAI,
                new BigDecimal("71000"),
                new BigDecimal("71500"),
                BigDecimal.TEN,
                new BigDecimal("-0.0070"),
                Currency.KRW,
                at);
    }

    @Test
    void quotaIsPerUserAndSumsAcrossOwnAccounts() {
        Fixtures f = new Fixtures(jdbc);
        UserId a = userWithKakao(12001);
        UserId b = userWithKakao(12002);
        long samsung = f.samsung();
        long a1 = f.account(a.value(), "A1");
        long a2 = f.account(a.value(), "A2");
        long aHolding1 = f.holding(a1, samsung, "71500", "10");
        long aHolding2 = f.holding(a2, samsung, "70000", "1");
        long b1 = f.account(b.value(), "B1");
        long bHolding = f.holding(b1, samsung, "70000", "1");
        Instant now = clock.instant();

        assertThat(store.reserve(reservation(a, samsung, aHolding1, a1, now))).isPresent();
        assertThat(store.reserve(reservation(a, samsung, aHolding2, a2, now))).isPresent();
        assertThat(store.reserve(reservation(a, samsung, aHolding1, a1, now)))
                .as("A는 두 계좌 합산 2회")
                .isEmpty();

        assertThat(guideService.quota(b, samsung).remaining()).isEqualTo(2);
        assertThat(store.reserve(reservation(b, samsung, bHolding, b1, now))).isPresent();
        assertThat(guideService.quota(a, samsung).remaining()).isZero();
    }

    @Test
    void concurrentRequestsFromTwoUsersEachStayWithinLimit() throws Exception {
        Fixtures f = new Fixtures(jdbc);
        UserId a = userWithKakao(12003);
        UserId b = userWithKakao(12004);
        long samsung = f.samsung();
        long aAccount = f.account(a.value(), "A");
        long bAccount = f.account(b.value(), "B");
        long aHolding = f.holding(aAccount, samsung, "71500", "10");
        long bHolding = f.holding(bAccount, samsung, "70000", "1");
        Instant now = clock.instant();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < 100; round++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<OptionalLong>> results = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    boolean forA = i % 2 == 0;
                    GuideReservation r = forA
                            ? reservation(a, samsung, aHolding, aAccount, now)
                            : reservation(b, samsung, bHolding, bAccount, now);
                    results.add(executor.submit(() -> {
                        start.await();
                        return store.reserve(r);
                    }));
                }
                start.countDown();
                for (Future<OptionalLong> result : results) {
                    result.get();
                }
                for (UserId user : List.of(a, b)) {
                    assertThat(jdbc.sql("SELECT count(*) FROM guide_request WHERE user_id = :u AND status <> 'FAILED'")
                                    .param("u", user.value())
                                    .query(Long.class)
                                    .single())
                            .as("round %d user %s", round, user)
                            .isEqualTo(2);
                }
                jdbc.sql("DELETE FROM guide_request").update();
            }
        }
    }
}
