package com.stockmanagement.market.adapter.toss;

import com.stockmanagement.market.application.IndicatorReading;
import com.stockmanagement.market.application.port.DomesticIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 국내 지수(시장 지표)·원달러 환율(003 contracts/external-apis.md). 토스 호출은 고정 IP 프록시를 거친다(TossHttpClient).
 * 실패는 예외 대신 빈 결과로 돌려 지표별로 격리한다.
 */
@Component
class TossIndicatorSource implements DomesticIndicatorSource {

    private static final Logger log = LoggerFactory.getLogger(TossIndicatorSource.class);

    private static final Map<String, MarketIndicator> INDEX_SYMBOLS =
            Map.of("KOSPI", MarketIndicator.KOSPI, "KOSDAQ", MarketIndicator.KOSDAQ);

    private static final ParameterizedTypeReference<TossHttpClient.Envelope<List<IndicatorPrice>>> PRICES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TossHttpClient.Envelope<CandlePage>> CANDLES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TossHttpClient.Envelope<ExchangeRate>> EXCHANGE_RATE =
            new ParameterizedTypeReference<>() {};

    private final TossHttpClient httpClient;
    private final TossTokenStore tokenStore;

    TossIndicatorSource(TossHttpClient httpClient, TossTokenStore tokenStore) {
        this.httpClient = httpClient;
        this.tokenStore = tokenStore;
    }

    /**
     * GET /api/v1/market-indicators/prices?symbols=KOSPI,KOSDAQ (MARKET_INDICATOR). 장 마감 후에는 timestamp가 null이고
     * lastPrice(종가)만 온다. 그때 기준 시각은 비워 두고 갱신기가 정한다. 값이 0 이하이면 미제공으로 본다.
     */
    @Override
    public Map<MarketIndicator, IndicatorReading> currentIndices() {
        Map<MarketIndicator, IndicatorReading> result = new EnumMap<>(MarketIndicator.class);
        attempt(
                        "지수 현재가",
                        () -> withToken(token -> httpClient.get(
                                TossRateLimiter.Group.MARKET_INDICATOR,
                                token,
                                uri -> uri.path("/api/v1/market-indicators/prices")
                                        .queryParam("symbols", "KOSPI,KOSDAQ")
                                        .build(),
                                PRICES)))
                .orElse(List.of())
                .stream()
                .filter(p -> INDEX_SYMBOLS.containsKey(p.symbol()) && p.lastPrice() != null)
                .filter(p -> new BigDecimal(p.lastPrice()).signum() > 0)
                .forEach(p -> result.put(
                        INDEX_SYMBOLS.get(p.symbol()),
                        new IndicatorReading(
                                new BigDecimal(p.lastPrice()),
                                p.timestamp() == null ? null : p.timestamp().toInstant())));
        return result;
    }

    /** GET /api/v1/market-indicators/{symbol}/candles?interval=1d&count=5 (MARKET_INDICATOR_CHART), 최신순. */
    @Override
    public Optional<BigDecimal> previousIndexClose(MarketIndicator index, LocalDate sessionDate) {
        return attempt(
                        "지수 일봉 " + index,
                        () -> withToken(token -> httpClient.get(
                                TossRateLimiter.Group.MARKET_INDICATOR_CHART,
                                token,
                                uri -> uri.path("/api/v1/market-indicators/{symbol}/candles")
                                        .queryParam("interval", "1d")
                                        .queryParam("count", 5)
                                        .build(index.name()),
                                CANDLES)))
                .flatMap(page -> page.candles().stream()
                        .filter(c -> c.tradingDate().isBefore(sessionDate))
                        .findFirst()
                        .map(c -> new BigDecimal(c.closePrice())));
    }

    @Override
    public Optional<IndicatorReading> currentUsdKrw() {
        return exchangeRate(null)
                .map(r -> new IndicatorReading(
                        new BigDecimal(r.midRate()), r.validFrom().toInstant()));
    }

    @Override
    public Optional<BigDecimal> usdKrwAt(Instant at) {
        return exchangeRate(at).map(r -> new BigDecimal(r.midRate()));
    }

    /** GET /api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW[&dateTime=] (MARKET_INFO). 매매기준율(midRate)을 쓴다. */
    private Optional<ExchangeRate> exchangeRate(Instant at) {
        return attempt(
                "환율",
                () -> withToken(token -> httpClient.get(
                        TossRateLimiter.Group.MARKET_INFO,
                        token,
                        uri -> {
                            uri.path("/api/v1/exchange-rate")
                                    .queryParam("baseCurrency", "USD")
                                    .queryParam("quoteCurrency", "KRW");
                            if (at != null) {
                                uri.queryParam("dateTime", at.toString());
                            }
                            return uri.build();
                        },
                        EXCHANGE_RATE)));
    }

    private static <T> Optional<T> attempt(String what, java.util.function.Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (RuntimeException e) {
            log.warn("시장 지표 조회 실패: {}, type={}", what, e.getClass().getSimpleName());
            return Optional.empty();
        }
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

    record IndicatorPrice(String symbol, OffsetDateTime timestamp, String lastPrice) {}

    record CandlePage(List<Candle> candles) {
        CandlePage {
            candles = candles == null ? List.of() : candles;
        }
    }

    /** 일봉 timestamp는 현지 자정이다. 문자열로 받아 현지 날짜를 읽는다. */
    record Candle(String timestamp, String closePrice) {
        LocalDate tradingDate() {
            return OffsetDateTime.parse(timestamp).toLocalDate();
        }
    }

    record ExchangeRate(String midRate, OffsetDateTime validFrom) {}
}
