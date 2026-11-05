package com.stockmanagement.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.StaleGuideSweeper;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.GuideRequest;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 정체 정리(T099): 3분 넘게 PENDING이면 FAILED(TRANSIENT_ERROR)가 되고 슬롯이 반환된다. */
class StaleGuideSweeperIT extends IntegrationTestBase {

    @Autowired
    private GuideStore store;

    @Autowired
    private StaleGuideSweeper sweeper;

    @Test
    void failsPendingOlderThanThreeMinutesAndReturnsSlot() {
        Fixtures f = new Fixtures(jdbc);
        long account = f.account("키움 일반");
        long samsung = f.samsung();
        long holding = f.holding(account, samsung, "71500", "10");
        Instant start = clock.instant();
        GuideReservation reservation = new GuideReservation(
                samsung,
                holding,
                account,
                "키움 일반",
                GuideRequest.usageDateOf(start),
                AiProvider.OPENAI,
                new BigDecimal("71000"),
                new BigDecimal("71500"),
                BigDecimal.TEN,
                new BigDecimal("-0.0070"),
                Currency.KRW,
                start);
        long old = store.reserve(reservation).getAsLong();
        store.reserve(reservation);

        clock.advance(Duration.ofMinutes(3));
        sweeper.sweep();
        assertThat(store.find(old).orElseThrow().isPending())
                .as("정확히 3분은 아직 정체가 아니다")
                .isTrue();

        clock.advance(Duration.ofSeconds(1));
        sweeper.sweep();

        assertThat(jdbc.sql(
                                "SELECT count(*) FROM guide_request WHERE status = 'FAILED' AND failure_kind = 'TRANSIENT_ERROR'")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(store.reserve(reservation)).isPresent();
    }
}
