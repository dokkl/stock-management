package com.stockmanagement.market.adapter.toss;

import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketCalendar;
import com.stockmanagement.market.domain.QuoteResult;
import com.stockmanagement.market.domain.SecurityListing;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/** 토스증권 Open API 기반 MarketDataProvider(research R2·R3). */
@Component
class TossMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TossMarketDataProvider.class);

    /** 현재가·종목 정보 API의 다건 조회 상한. */
    static final int BATCH_SIZE = 200;

    /** 직전 거래일 종가를 찾기 위해 받는 일봉 수(연휴를 넘길 만큼). */
    private static final int PREVIOUS_CLOSE_CANDLES = 10;

    /** 보유 종목으로 등록할 수 없는 유형(신주인수권 등)은 마스터에서 뺀다. */
    private static final Set<String> EXCLUDED_TYPES = Set.of("STOCK_WARRANTS");

    private static final ParameterizedTypeReference<TossHttpClient.Envelope<List<ListedStock>>> LISTED_STOCKS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TossHttpClient.Envelope<List<Price>>> PRICES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TossHttpClient.Envelope<CalendarResponse>> CALENDAR =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TossHttpClient.Envelope<CandlePage>> CANDLES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TossHttpClient.Envelope<List<StockInfo>>> STOCKS =
            new ParameterizedTypeReference<>() {};

    private final TossHttpClient httpClient;
    private final TossTokenStore tokenStore;
    private final Clock clock;

    TossMarketDataProvider(TossHttpClient httpClient, TossTokenStore tokenStore, Clock clock) {
        this.httpClient = httpClient;
        this.tokenStore = tokenStore;
        this.clock = clock;
    }

    /** GET /api/v1/stocks/all?market={exchange}&status=ACTIVE (STOCK_ALL 그룹, 초당 1회). */
    @Override
    public List<SecurityListing> fetchSecurityMaster(Exchange exchange) {
        List<ListedStock> stocks = withToken(token -> httpClient.get(
                TossRateLimiter.Group.STOCK_ALL,
                token,
                uri -> uri.path("/api/v1/stocks/all")
                        .queryParam("market", exchange.name())
                        .queryParam("status", "ACTIVE")
                        .build(),
                LISTED_STOCKS));
        return stocks.stream()
                .filter(s -> !EXCLUDED_TYPES.contains(s.securityType()))
                .map(s -> new SecurityListing(exchange, s.symbol(), s.name(), null))
                .toList();
    }

    /** GET /api/v1/prices?symbols=… (MARKET_DATA 그룹). 200종목씩 나누고, 배치가 실패하면 그 배치만 실패로 돌린다. */
    @Override
    public List<QuoteResult> fetchQuotes(List<String> symbols) {
        List<QuoteResult> results = new ArrayList<>(symbols.size());
        for (List<String> batch : batches(symbols)) {
            results.addAll(fetchQuoteBatch(batch));
        }
        return results;
    }

    private List<QuoteResult> fetchQuoteBatch(List<String> batch) {
        List<Price> prices;
        try {
            prices = withToken(token -> httpClient.get(
                    TossRateLimiter.Group.MARKET_DATA,
                    token,
                    uri -> uri.path("/api/v1/prices")
                            .queryParam("symbols", String.join(",", batch))
                            .build(),
                    PRICES));
        } catch (RestClientException | IllegalStateException e) {
            QuoteResult.Reason reason = failureReason(e);
            log.warn("토스증권 현재가 조회 실패: symbols={}, reason={}", batch.size(), reason, e);
            return batch.stream()
                    .<QuoteResult>map(symbol -> new QuoteResult.Failure(symbol, reason))
                    .toList();
        }
        Map<String, Price> bySymbol =
                prices.stream().collect(Collectors.toMap(Price::symbol, Function.identity(), (a, b) -> a));
        Instant fetchedAt = clock.instant();
        return batch.stream()
                .map(symbol -> toResult(symbol, bySymbol.get(symbol), fetchedAt))
                .toList();
    }

    private static QuoteResult toResult(String symbol, Price price, Instant fetchedAt) {
        if (price == null || price.lastPrice() == null) {
            return new QuoteResult.Failure(symbol, QuoteResult.Reason.NOT_FOUND);
        }
        Instant asOf = price.timestamp() == null ? fetchedAt : price.timestamp().toInstant();
        return new QuoteResult.Success(symbol, new BigDecimal(price.lastPrice()), asOf);
    }

    private static QuoteResult.Reason failureReason(RuntimeException e) {
        return e instanceof HttpClientErrorException.TooManyRequests
                ? QuoteResult.Reason.RATE_LIMITED
                : QuoteResult.Reason.UPSTREAM_ERROR;
    }

    /** GET /api/v1/market-calendar/{KR|US} (MARKET_INFO 그룹). 국내는 통합(KRX+NXT) 정규장 기준이다. */
    @Override
    public MarketCalendar fetchMarketCalendar(Market market) {
        CalendarResponse response = withToken(token -> httpClient.get(
                TossRateLimiter.Group.MARKET_INFO,
                token,
                uri -> uri.path("/api/v1/market-calendar/" + market.name()).build(),
                CALENDAR));
        Session regular = response.today().regularSession();
        return new MarketCalendar(
                market,
                response.today().date(),
                regular == null ? null : regular.startTime().toInstant(),
                regular == null ? null : regular.endTime().toInstant(),
                response.previousBusinessDay().date());
    }

    /** GET /api/v1/candles?interval=1d (MARKET_DATA_CHART 그룹). 일봉 시각은 현지 자정이라 날짜만 비교한다. */
    @Override
    public Optional<BigDecimal> fetchPreviousClose(String symbol, LocalDate referenceDate) {
        CandlePage page = withToken(token -> httpClient.get(
                TossRateLimiter.Group.MARKET_DATA_CHART,
                token,
                uri -> uri.path("/api/v1/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1d")
                        .queryParam("count", PREVIOUS_CLOSE_CANDLES)
                        .build(),
                CANDLES));
        return page.candles().stream()
                .filter(c -> c.tradingDate().isBefore(referenceDate))
                .findFirst()
                .map(c -> new BigDecimal(c.closePrice()));
    }

    /** GET /api/v1/stocks?symbols=… (STOCK 그룹). 거래정지 여부는 국내 종목(KRX)에만 제공된다. */
    @Override
    public Set<String> fetchSuspendedSymbols(List<String> symbols) {
        Set<String> suspended = new HashSet<>();
        for (List<String> batch : batches(symbols)) {
            List<StockInfo> infos = withToken(token -> httpClient.get(
                    TossRateLimiter.Group.STOCK,
                    token,
                    uri -> uri.path("/api/v1/stocks")
                            .queryParam("symbols", String.join(",", batch))
                            .build(),
                    STOCKS));
            infos.stream().filter(StockInfo::isSuspended).map(StockInfo::symbol).forEach(suspended::add);
        }
        return suspended;
    }

    private static List<List<String>> batches(List<String> symbols) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < symbols.size(); i += BATCH_SIZE) {
            batches.add(symbols.subList(i, Math.min(i + BATCH_SIZE, symbols.size())));
        }
        return batches;
    }

    /** 401(토큰 만료·무효)이면 토큰을 한 번 재발급해 다시 시도한다. */
    private <T> T withToken(Function<String, T> call) {
        try {
            return call.apply(tokenStore.accessToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            tokenStore.invalidate();
            return call.apply(tokenStore.accessToken());
        }
    }

    record ListedStock(String symbol, String name, String securityType, Boolean isCommonShare) {}

    record Price(String symbol, OffsetDateTime timestamp, String lastPrice, String currency) {}

    record CalendarResponse(MarketDay today, MarketDay previousBusinessDay) {}

    /** 국내는 integrated.regularMarket, 미국은 regularMarket에 정규장이 온다. 휴장이면 둘 다 null. */
    record MarketDay(LocalDate date, Integrated integrated, Session regularMarket) {
        Session regularSession() {
            return integrated != null ? integrated.regularMarket() : regularMarket;
        }
    }

    record Integrated(Session regularMarket) {}

    record Session(OffsetDateTime startTime, OffsetDateTime endTime) {}

    record CandlePage(List<Candle> candles) {}

    /** 일봉 timestamp는 현지 자정이다. Jackson이 UTC로 바꾸면 날짜가 밀리므로 문자열로 받아 현지 날짜를 읽는다. */
    record Candle(String timestamp, String closePrice) {
        LocalDate tradingDate() {
            return OffsetDateTime.parse(timestamp).toLocalDate();
        }
    }

    record StockInfo(String symbol, KoreanMarketDetail koreanMarketDetail) {
        boolean isSuspended() {
            return koreanMarketDetail != null && Boolean.TRUE.equals(koreanMarketDetail.krxTradingSuspended());
        }
    }

    record KoreanMarketDetail(Boolean krxTradingSuspended) {}
}
