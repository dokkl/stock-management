package com.stockmanagement.market.application;

import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.application.port.QuoteStore;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketCalendar;
import com.stockmanagement.market.domain.MarketState;
import com.stockmanagement.market.domain.Quote;
import com.stockmanagement.market.domain.QuoteResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 보유 종목 시세를 미리 받아 quote 테이블에 저장한다(research R3). 화면은 DB만 읽는다.
 *
 * <ul>
 *   <li>장중: 매 실행마다 현재가를 갱신한다.
 *   <li>장 마감: 마감 후 한 번만 종가를 확정하고 더 호출하지 않는다.
 *   <li>장 시작 전·휴장: 시세가 없거나 거래일이 지난 종목만 받는다.
 *   <li>실패한 종목은 기존 값과 수신 시각을 그대로 둔다(화면은 "지연"으로 표시).
 * </ul>
 */
@Service
public class QuoteRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefreshScheduler.class);

    private final MarketDataProvider provider;
    private final QuoteStore store;
    private final MarketCalendarCache calendars;
    private final Clock clock;

    public QuoteRefreshScheduler(
            MarketDataProvider provider, QuoteStore store, MarketCalendarCache calendars, Clock clock) {
        this.provider = provider;
        this.store = store;
        this.calendars = calendars;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.quote.refresh-interval}")
    public void refresh() {
        for (Market market : Market.values()) {
            refresh(market);
        }
    }

    void refresh(Market market) {
        List<HeldSecurity> held = store.findHeldSecurities(market);
        if (held.isEmpty()) {
            return;
        }
        Optional<MarketCalendar> calendar = calendars.get(market);
        if (calendar.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Session session = new Session(calendar.get(), now);
        store.updateMarketState(market, session.state());
        List<HeldSecurity> targets = held.stream().filter(session::needsFetch).toList();
        if (targets.isEmpty()) {
            return;
        }
        refreshSuspension(targets);
        Map<String, HeldSecurity> bySymbol =
                targets.stream().collect(Collectors.toMap(HeldSecurity::symbol, Function.identity(), (a, b) -> a));
        int saved = 0;
        for (QuoteResult result : provider.fetchQuotes(List.copyOf(bySymbol.keySet()))) {
            if (result instanceof QuoteResult.Success success
                    && save(bySymbol.get(success.symbol()), success, session, now)) {
                saved++;
            }
        }
        log.debug("시세 갱신: market={}, state={}, targets={}, saved={}", market, session.state(), targets.size(), saved);
    }

    private boolean save(HeldSecurity held, QuoteResult.Success success, Session session, Instant now) {
        Optional<BigDecimal> prevClose = previousClose(held, session.date());
        if (prevClose.isEmpty()) {
            return false;
        }
        store.save(new Quote(
                held.securityId(),
                success.price(),
                prevClose.get(),
                session.date(),
                session.state(),
                success.asOf(),
                now));
        return true;
    }

    /** 같은 거래일의 전일 종가는 이미 저장되어 있으므로 다시 받지 않는다(종목당 하루 1회). */
    private Optional<BigDecimal> previousClose(HeldSecurity held, LocalDate sessionDate) {
        Quote existing = held.quote();
        if (existing != null && existing.sessionDate().equals(sessionDate)) {
            return Optional.of(existing.prevClose());
        }
        try {
            Optional<BigDecimal> close = provider.fetchPreviousClose(held.symbol(), sessionDate);
            if (close.isEmpty()) {
                log.warn("전일 종가 없음, 시세 저장 건너뜀: symbol={}, sessionDate={}", held.symbol(), sessionDate);
            }
            return close;
        } catch (RuntimeException e) {
            log.warn("전일 종가 조회 실패, 시세 저장 건너뜀: symbol={}", held.symbol(), e);
            return Optional.empty();
        }
    }

    /** 거래정지 여부는 실패해도 시세 갱신을 막지 않는다. */
    private void refreshSuspension(List<HeldSecurity> targets) {
        try {
            Set<String> suspended = provider.fetchSuspendedSymbols(
                    targets.stream().map(HeldSecurity::symbol).toList());
            Map<Boolean, List<Long>> partition = targets.stream()
                    .collect(Collectors.partitioningBy(
                            h -> suspended.contains(h.symbol()),
                            Collectors.mapping(HeldSecurity::securityId, Collectors.toList())));
            store.updateSuspension(partition.get(true), partition.get(false));
        } catch (RuntimeException e) {
            log.warn("거래정지 여부 조회 실패: targets={}", targets.size(), e);
        }
    }

    /** 이번 실행 시점의 장 상태와 시세가 속할 거래일. */
    private record Session(MarketCalendar calendar, MarketState state, LocalDate date) {

        Session(MarketCalendar calendar, Instant now) {
            this(calendar, calendar.state(now), calendar.sessionDate(now));
        }

        boolean needsFetch(HeldSecurity held) {
            Quote quote = held.quote();
            if (quote == null || !quote.sessionDate().equals(date)) {
                return true;
            }
            return switch (state) {
                case OPEN -> true;
                case CLOSED -> quote.fetchedAt().isBefore(calendar.regularClose());
                case PRE_OPEN, HOLIDAY -> false;
            };
        }
    }
}
