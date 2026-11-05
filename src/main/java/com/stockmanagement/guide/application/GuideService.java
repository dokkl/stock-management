package com.stockmanagement.guide.application;

import com.stockmanagement.aicredential.application.CredentialService;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.KstDate;
import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.GuideQuota;
import com.stockmanagement.guide.domain.GuideRequest;
import com.stockmanagement.market.domain.TradingStatus;
import com.stockmanagement.portfolio.domain.HoldingValuation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;

/**
 * 가이드 요청 접수(research R7·R8). 슬롯을 예약하고 바로 돌아가며, AI 호출은 GuideRunner가 백그라운드에서 한다.
 * 예약은 단일 INSERT라 트랜잭션을 두지 않는다: 실행기에 넘기기 전에 행이 커밋되어 있어야 한다.
 */
@Service
public class GuideService {

    private final GuideStore store;
    private final CredentialService credentials;
    private final GuideRunner runner;
    private final Clock clock;

    public GuideService(GuideStore store, CredentialService credentials, GuideRunner runner, Clock clock) {
        this.store = store;
        this.credentials = credentials;
        this.runner = runner;
        this.clock = clock;
    }

    public GuideRequestResult request(long holdingId) {
        GuideTarget target =
                store.findTarget(holdingId).orElseThrow(() -> new ResourceNotFoundException("holding", holdingId));
        Optional<AiProvider> provider = credentials.selectedProvider();
        if (provider.isEmpty()) {
            return new GuideRequestResult.NoCredential();
        }
        if (!target.isActive()) {
            return new GuideRequestResult.Unavailable(
                    target.tradingStatus() == TradingStatus.DELISTED
                            ? GuideRequestResult.Reason.DELISTED
                            : GuideRequestResult.Reason.SUSPENDED);
        }
        if (!target.hasQuote()) {
            return new GuideRequestResult.Unavailable(GuideRequestResult.Reason.NO_QUOTE);
        }
        Instant now = clock.instant();
        LocalDate usageDate = GuideRequest.usageDateOf(now);
        OptionalLong pending = store.findPending(target.securityId(), usageDate);
        if (pending.isPresent()) {
            return new GuideRequestResult.Accepted(pending.getAsLong());
        }
        OptionalLong reserved = store.reserve(reservation(target, provider.get(), usageDate, now));
        if (reserved.isEmpty()) {
            return new GuideRequestResult.QuotaExhausted(GuideQuota.resetsAt(usageDate));
        }
        runner.submit(reserved.getAsLong());
        return new GuideRequestResult.Accepted(reserved.getAsLong());
    }

    /** 오늘(KST) 이 종목의 한도 사용 현황. */
    public GuideQuota quota(long securityId) {
        return new GuideQuota(store.countActive(securityId, KstDate.today(clock)));
    }

    public Optional<GuideView> find(long guideId) {
        return store.find(guideId);
    }

    private static GuideReservation reservation(
            GuideTarget target, AiProvider provider, LocalDate usageDate, Instant now) {
        HoldingValuation valuation =
                HoldingValuation.of(target.avgPrice(), target.quantity(), target.price(), target.currency());
        return new GuideReservation(
                target.securityId(),
                target.holdingId(),
                target.accountId(),
                target.accountName(),
                usageDate,
                provider,
                target.price(),
                target.avgPrice(),
                target.quantity(),
                valuation.returnRate(),
                target.currency(),
                now);
    }
}
