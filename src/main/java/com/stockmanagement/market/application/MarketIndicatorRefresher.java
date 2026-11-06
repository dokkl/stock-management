package com.stockmanagement.market.application;

import com.stockmanagement.common.domain.KstDate;
import com.stockmanagement.market.application.port.DomesticIndicatorSource;
import com.stockmanagement.market.application.port.GlobalIndicatorSource;
import com.stockmanagement.market.application.port.IndicatorStore;
import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketCalendar;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.market.domain.MarketState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시장 지표 갱신(003 research R4). 화면은 저장값만 읽고 외부 호출은 여기서만 한다(사용자 수와 무관).
 *
 * <ul>
 *   <li>국내 지수·환율: 국내 장중 1분마다, 장 외에는 closedInterval마다. 전일 값은 같은 날이면 저장된 값을 다시 쓴다.
 *   <li>미국 지표: globalInterval마다. 제공처(Cboe·야후·FRED) 중 가장 최신 날짜를 쓴다.
 * </ul>
 * 한 지표가 실패해도 나머지는 저장하고, 실패한 지표의 기존 값은 그대로 둔다.
 */
@Component
public class MarketIndicatorRefresher {

    private static final Logger log = LoggerFactory.getLogger(MarketIndicatorRefresher.class);

    /** 원·달러 전일 비교 시각: 직전 국내 영업일 15:30(KST). */
    static final LocalTime FX_REFERENCE_TIME = LocalTime.of(15, 30);

    private final DomesticIndicatorSource domestic;
    private final List<GlobalIndicatorSource> globals;
    private final IndicatorStore store;
    private final MarketCalendarCache calendars;
    private final IndicatorProperties properties;
    private final Clock clock;
    private volatile Instant lastDomesticRun;

    public MarketIndicatorRefresher(
            DomesticIndicatorSource domestic,
            List<GlobalIndicatorSource> globals,
            IndicatorStore store,
            MarketCalendarCache calendars,
            IndicatorProperties properties,
            Clock clock) {
        this.domestic = domestic;
        this.globals = globals;
        this.store = store;
        this.calendars = calendars;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.indicator.domestic-check-interval}")
    public void refreshDomestic() {
        Instant now = clock.instant();
        Optional<MarketCalendar> calendar = calendars.get(Market.KR);
        if (calendar.isEmpty() || !dueDomestic(calendar.get(), now)) {
            return;
        }
        lastDomesticRun = now;
        Map<MarketIndicator, IndicatorQuote> existing = existing();
        LocalDate sessionDate = calendar.get().sessionDate(now);
        domestic.currentIndices()
                .forEach((index, reading) -> save(new IndicatorQuote(
                        index,
                        reading.value(),
                        previous(
                                existing.get(index),
                                sessionDate,
                                () -> domestic.previousIndexClose(index, sessionDate)),
                        reading.asOf() != null ? reading.asOf() : fallbackAsOf(calendar.get(), now),
                        sessionDate,
                        now)));
        LocalDate today = KstDate.today(clock);
        Instant fxReference = calendar.get()
                .previousBusinessDay()
                .atTime(FX_REFERENCE_TIME)
                .atZone(KstDate.ZONE)
                .toInstant();
        domestic.currentUsdKrw()
                .ifPresent(reading -> save(new IndicatorQuote(
                        MarketIndicator.USD_KRW,
                        reading.value(),
                        previous(existing.get(MarketIndicator.USD_KRW), today, () -> domestic.usdKrwAt(fxReference)),
                        reading.asOf(),
                        today,
                        now)));
    }

    @Scheduled(fixedDelayString = "${app.indicator.global-interval}", initialDelayString = "PT20S")
    public void refreshGlobal() {
        Instant now = clock.instant();
        for (MarketIndicator indicator : MarketIndicator.values()) {
            if (indicator.isRealtime()) {
                continue;
            }
            latestAcross(indicator)
                    .ifPresent(o -> save(new IndicatorQuote(
                            indicator,
                            o.value(),
                            o.previousValue(),
                            o.date().atStartOfDay(ZoneOffset.UTC).toInstant(),
                            o.date(),
                            now)));
        }
    }

    /**
     * 제공처 중 가장 최신 날짜의 관측치. 같은 날짜면 우선순위(@Order)가 앞선 제공처. Cboe·야후가 실패하거나 늦으면 FRED가 쓰인다.
     */
    private Optional<DailyObservation> latestAcross(MarketIndicator indicator) {
        DailyObservation best = null;
        for (GlobalIndicatorSource source : globals) {
            Optional<DailyObservation> found = source.latest(indicator);
            if (found.isPresent() && (best == null || found.get().date().isAfter(best.date()))) {
                best = found.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /** 기준 시각을 받지 못했을 때(장 마감 후 토스는 null을 준다): 마감 뒤에는 정규장 종료 시각, 그 밖에는 받은 시각. */
    private static Instant fallbackAsOf(MarketCalendar calendar, Instant now) {
        return calendar.state(now) == MarketState.CLOSED && calendar.regularClose() != null
                ? calendar.regularClose()
                : now;
    }

    /** 장중이면 매번, 장 외에는 closedInterval이 지났을 때만. */
    private boolean dueDomestic(MarketCalendar calendar, Instant now) {
        Instant last = lastDomesticRun;
        return calendar.state(now) == MarketState.OPEN
                || last == null
                || !last.plus(properties.closedInterval()).isAfter(now);
    }

    /** 같은 날의 전일 값은 이미 저장되어 있으므로 다시 받지 않는다(지표당 하루 1회). */
    private static BigDecimal previous(IndicatorQuote existing, LocalDate date, Supplier<Optional<BigDecimal>> fetch) {
        if (existing != null && existing.asOfDate().equals(date) && existing.previousClose() != null) {
            return existing.previousClose();
        }
        return fetch.get().orElse(null);
    }

    private Map<MarketIndicator, IndicatorQuote> existing() {
        Map<MarketIndicator, IndicatorQuote> map = new EnumMap<>(MarketIndicator.class);
        store.findAll().forEach(q -> map.put(q.indicator(), q));
        return map;
    }

    private void save(IndicatorQuote quote) {
        try {
            store.save(quote);
        } catch (RuntimeException e) {
            log.warn(
                    "시장 지표 저장 실패: indicator={}, type={}",
                    quote.indicator(),
                    e.getClass().getSimpleName());
        }
    }

    /** 테스트용: 장 외 갱신 간격 판단을 초기화한다. */
    void resetSchedule() {
        lastDomesticRun = null;
    }
}
