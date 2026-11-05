package com.stockmanagement.guide.application;

import com.stockmanagement.aicredential.application.CredentialService;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.FailureKind;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;
import com.stockmanagement.guide.domain.GuidePromptBuilder;
import com.stockmanagement.guide.domain.GuideStatus;
import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.DailyCandle;
import com.stockmanagement.market.domain.TradingStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 가이드 백그라운드 실행(research R8): 일봉 조회 → 프롬프트 → 키 복호화 → AI 호출 → 결과 기록. 가상 스레드에서 돈다.
 * 어떤 경우에도 요청을 PENDING으로 남기지 않는다: 성공이면 SUCCEEDED, 아니면 FAILED(슬롯 반환)다.
 */
@Component
public class GuideRunner {

    private static final Logger log = LoggerFactory.getLogger(GuideRunner.class);
    static final String TIMER = "guide.generation.duration";

    private final GuideStore store;
    private final CredentialService credentials;
    private final MarketDataProvider market;
    private final Map<AiProvider, AiGuideProvider> providers = new EnumMap<>(AiProvider.class);
    private final TaskExecutor executor;
    private final MeterRegistry meters;
    private final Clock clock;

    public GuideRunner(
            GuideStore store,
            CredentialService credentials,
            MarketDataProvider market,
            List<AiGuideProvider> providers,
            @Qualifier("guideExecutor") TaskExecutor executor,
            MeterRegistry meters,
            Clock clock) {
        this.store = store;
        this.credentials = credentials;
        this.market = market;
        this.executor = executor;
        this.meters = meters;
        this.clock = clock;
        providers.forEach(p -> this.providers.put(p.provider(), p));
    }

    public void submit(long guideId) {
        executor.execute(() -> run(guideId));
    }

    /** 요청 1건을 끝까지 처리한다. 예상하지 못한 예외도 일시 장애 실패로 기록하고 다시 던지지 않는다(실행기 스레드). */
    public void run(long guideId) {
        Optional<GuideJob> job = store.findJob(guideId);
        if (job.isEmpty() || job.get().status() != GuideStatus.PENDING) {
            return;
        }
        String outcome;
        try {
            outcome = execute(job.get());
        } catch (RuntimeException e) {
            log.error("가이드 실행 중 예외: guideId={}, type={}", guideId, e.getClass().getName(), e);
            store.markFailed(guideId, FailureKind.TRANSIENT_ERROR, clock.instant());
            outcome = FailureKind.TRANSIENT_ERROR.name();
        }
        Timer.builder(TIMER)
                .description("가이드 요청부터 완료까지 걸린 시간(SC-005)")
                .tag("provider", job.get().provider().name())
                .tag("outcome", outcome)
                .register(meters)
                .record(Duration.between(job.get().requestedAt(), clock.instant()));
    }

    private String execute(GuideJob job) {
        if (job.tradingStatus() != TradingStatus.ACTIVE) {
            return fail(job, FailureKind.INVALID_STATE);
        }
        Optional<ApiKey> key = credentials.keyFor(job.provider());
        AiGuideProvider provider = providers.get(job.provider());
        if (key.isEmpty() || provider == null) {
            return fail(job, FailureKind.CREDENTIAL_ERROR);
        }
        List<DailyCandle> candles;
        try {
            candles = market.fetchDailyCandles(job.symbol(), GuidePromptBuilder.LOOKBACK_DAYS);
        } catch (RuntimeException e) {
            log.warn(
                    "가이드용 일봉 조회 실패: guideId={}, type={}", job.id(), e.getClass().getSimpleName());
            return fail(job, FailureKind.TRANSIENT_ERROR);
        }
        return record(job, provider.generateGuide(key.get(), prompt(job, candles)));
    }

    private static GuidePrompt prompt(GuideJob job, List<DailyCandle> candles) {
        List<BigDecimal> closes = candles.isEmpty()
                ? List.of(job.price())
                : candles.stream().map(DailyCandle::close).toList();
        return GuidePromptBuilder.build(
                new GuidePromptBuilder.Stock(job.market(), job.symbol(), job.name(), job.currency()),
                job.price(),
                job.prevClose() == null ? job.price() : job.prevClose(),
                closes,
                job.avgPrice(),
                job.quantity());
    }

    private String record(GuideJob job, GuideOutcome outcome) {
        Instant now = clock.instant();
        return switch (outcome) {
            case GuideOutcome.Generated g -> {
                if (!store.markSucceeded(job.id(), g.model(), g.content(), g.rawText(), now)) {
                    log.warn("가이드가 이미 종료되어 결과를 버림(정체 정리 후 완료): guideId={}", job.id());
                }
                yield GuideStatus.SUCCEEDED.name();
            }
            case GuideOutcome.CredentialError e -> fail(job, FailureKind.CREDENTIAL_ERROR);
            case GuideOutcome.TransientError e -> fail(job, FailureKind.TRANSIENT_ERROR);
        };
    }

    private String fail(GuideJob job, FailureKind kind) {
        store.markFailed(job.id(), kind, clock.instant());
        log.info("가이드 실패: guideId={}, provider={}, kind={}", job.id(), job.provider(), kind);
        return kind.name();
    }
}
