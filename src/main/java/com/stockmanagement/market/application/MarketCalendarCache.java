package com.stockmanagement.market.application;

import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 시장별 장 운영일 캐시. 시장 현지 날짜가 바뀌면 다시 받는다(하루 1회). */
@Component
public class MarketCalendarCache {

    private static final Logger log = LoggerFactory.getLogger(MarketCalendarCache.class);

    private final MarketDataProvider provider;
    private final Clock clock;
    private final Map<Market, Cached> cache = new ConcurrentHashMap<>();

    public MarketCalendarCache(MarketDataProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    /** 오늘 장 운영 정보. 받지 못하면 비어 있다(어제 정보로 장 상태를 판단하지 않는다). */
    public Optional<MarketCalendar> get(Market market) {
        LocalDate localToday = LocalDate.ofInstant(clock.instant(), market.zone());
        Cached cached = cache.get(market);
        if (cached != null && cached.fetchedOn().equals(localToday)) {
            return Optional.of(cached.calendar());
        }
        try {
            MarketCalendar calendar = provider.fetchMarketCalendar(market);
            cache.put(market, new Cached(calendar, localToday));
            return Optional.of(calendar);
        } catch (RuntimeException e) {
            log.warn("장 운영일 조회 실패, 이번 시세 갱신에서 제외: market={}", market, e);
            return Optional.empty();
        }
    }

    private record Cached(MarketCalendar calendar, LocalDate fetchedOn) {}
}
