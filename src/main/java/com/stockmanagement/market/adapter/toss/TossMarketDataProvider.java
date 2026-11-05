package com.stockmanagement.market.adapter.toss;

import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.SecurityListing;
import java.util.List;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/** 토스증권 Open API 기반 MarketDataProvider(research R2). */
@Component
class TossMarketDataProvider implements MarketDataProvider {

    /** 보유 종목으로 등록할 수 없는 유형(신주인수권 등)은 마스터에서 뺀다. */
    private static final Set<String> EXCLUDED_TYPES = Set.of("STOCK_WARRANTS");

    private static final ParameterizedTypeReference<TossHttpClient.Envelope<List<ListedStock>>> LISTED_STOCKS =
            new ParameterizedTypeReference<>() {};

    private final TossHttpClient httpClient;
    private final TossTokenStore tokenStore;

    TossMarketDataProvider(TossHttpClient httpClient, TossTokenStore tokenStore) {
        this.httpClient = httpClient;
        this.tokenStore = tokenStore;
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

    /** 401(토큰 만료·무효)이면 토큰을 한 번 재발급해 다시 시도한다. */
    private <T> T withToken(java.util.function.Function<String, T> call) {
        try {
            return call.apply(tokenStore.accessToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            tokenStore.invalidate();
            return call.apply(tokenStore.accessToken());
        }
    }

    record ListedStock(String symbol, String name, String securityType, Boolean isCommonShare) {}
}
