package com.stockmanagement.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.GuideService;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.FailureKind;
import com.stockmanagement.guide.domain.GuideRequest;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 슬롯 예약(research R7, T095): 하루 2슬롯, 실패 시 반환, 종목별 독립·계좌 합산, KST 자정 경계, 종료 상태 불변. */
class GuideSlotReservationIT extends IntegrationTestBase {

    @Autowired
    private GuideStore store;

    @Autowired
    private GuideService guideService;

    private Fixtures f;
    private long account;
    private long samsung;
    private long holding;

    @BeforeEach
    void setUp() {
        f = new Fixtures(jdbc);
        account = f.account("키움 일반");
        samsung = f.samsung();
        holding = f.holding(account, samsung, "71500", "10");
    }

    private GuideReservation reservation(long securityId, long holdingId, long accountId, Instant at) {
        return new GuideReservation(
                createOwner(),
                securityId,
                holdingId,
                accountId,
                "키움 일반",
                GuideRequest.usageDateOf(at),
                AiProvider.OPENAI,
                new BigDecimal("71000"),
                new BigDecimal("71500"),
                BigDecimal.TEN,
                new BigDecimal("-0.0070"),
                Currency.KRW,
                at);
    }

    private OptionalLong reserve() {
        return store.reserve(reservation(samsung, holding, account, clock.instant()));
    }

    private int slotOf(long id) {
        return jdbc.sql("SELECT slot FROM guide_request WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    @Test
    void twoSlotsThenQuotaExceeded() {
        OptionalLong first = reserve();
        OptionalLong second = reserve();

        assertThat(slotOf(first.getAsLong())).isEqualTo(1);
        assertThat(slotOf(second.getAsLong())).isEqualTo(2);
        assertThat(reserve()).isEmpty();
        assertThat(guideService.quota(createOwner(), samsung).remaining()).isZero();
    }

    @Test
    void failedRequestReturnsItsSlot() {
        long first = reserve().getAsLong();
        reserve();
        store.markFailed(first, FailureKind.TRANSIENT_ERROR, clock.instant());

        OptionalLong again = reserve();

        assertThat(again).isPresent();
        assertThat(slotOf(again.getAsLong())).isEqualTo(1);
        assertThat(guideService.quota(createOwner(), samsung).remaining()).isZero();
    }

    @Test
    void otherSecurityIsIndependent() {
        reserve();
        reserve();
        long apple = f.apple();
        long appleHolding = f.holding(account, apple, "185.2", "1");

        assertThat(store.reserve(reservation(apple, appleHolding, account, clock.instant())))
                .isPresent();
    }

    @Test
    void sameSecurityInDifferentAccountsSharesQuota() {
        long other = f.account("미래에셋");
        long otherHolding = f.holding(other, samsung, "70000", "1");

        assertThat(reserve()).isPresent();
        assertThat(store.reserve(reservation(samsung, otherHolding, other, clock.instant())))
                .isPresent();
        assertThat(store.reserve(reservation(samsung, otherHolding, other, clock.instant())))
                .isEmpty();
    }

    @Test
    void quotaResetsAtKstMidnight() {
        clock.set(Instant.parse("2026-11-05T14:59:00Z")); // 23:59 KST
        reserve();
        reserve();
        assertThat(reserve()).isEmpty();

        clock.set(Instant.parse("2026-11-05T15:00:01Z")); // 다음 날 00:00:01 KST

        assertThat(guideService.quota(createOwner(), samsung).remaining()).isEqualTo(2);
        assertThat(reserve()).isPresent();
        assertThat(reserve()).isPresent();
    }

    @Test
    void markOnlyChangesPendingAndFinalRowsAreImmutable() {
        long id = reserve().getAsLong();
        assertThat(store.markSucceeded(id, "gpt-5", null, "원문", clock.instant()))
                .isTrue();

        assertThat(store.markFailed(id, FailureKind.TRANSIENT_ERROR, clock.instant()))
                .isFalse();
        assertThatThrownBy(() -> jdbc.sql("UPDATE guide_request SET raw_text = '변조' WHERE id = :id")
                        .param("id", id)
                        .update())
                .hasMessageContaining("is final");
    }

    @Test
    void deletingHoldingKeepsFinalRecord() {
        long id = reserve().getAsLong();
        store.markSucceeded(id, "gpt-5", null, "원문", clock.instant());

        jdbc.sql("DELETE FROM holding WHERE id = :id").param("id", holding).update();

        assertThat(store.find(createOwner(), id)).hasValueSatisfying(g -> {
            assertThat(g.holdingId()).isNull();
            assertThat(g.rawText()).isEqualTo("원문");
            assertThat(g.accountName()).isEqualTo("키움 일반");
        });
    }
}
