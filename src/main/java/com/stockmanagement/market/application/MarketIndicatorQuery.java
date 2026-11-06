package com.stockmanagement.market.application;

import com.stockmanagement.market.application.port.IndicatorStore;
import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.IndicatorStatus;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.market.domain.MarketState;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 지표 영역 조회. 저장된 값만 읽는다(외부 호출 없음, 003 FR-009). */
@Service
public class MarketIndicatorQuery {

    private final IndicatorStore store;
    private final MarketCalendarCache calendars;
    private final IndicatorProperties properties;
    private final Clock clock;

    public MarketIndicatorQuery(
            IndicatorStore store, MarketCalendarCache calendars, IndicatorProperties properties, Clock clock) {
        this.store = store;
        this.calendars = calendars;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IndicatorBoard board() {
        Map<MarketIndicator, IndicatorQuote> quotes = new EnumMap<>(MarketIndicator.class);
        store.findAll().forEach(q -> quotes.put(q.indicator(), q));
        Instant now = clock.instant();
        MarketState domestic =
                calendars.cached(Market.KR).map(c -> c.state(now)).orElse(null);
        return new IndicatorBoard(Arrays.stream(MarketIndicator.values())
                .map(indicator -> card(indicator, quotes.get(indicator), domestic, now))
                .toList());
    }

    private IndicatorBoard.Card card(
            MarketIndicator indicator, IndicatorQuote quote, MarketState domestic, Instant now) {
        IndicatorStatus status =
                quote == null ? IndicatorStatus.UNAVAILABLE : quote.status(domestic, now, properties.staleAfter());
        return new IndicatorBoard.Card(indicator, quote, status);
    }
}
