package com.stockmanagement.market.adapter.fred;

import com.stockmanagement.market.application.DailyObservation;
import com.stockmanagement.market.application.port.GlobalIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FRED 일별 관측치(003 contracts/external-apis.md). 값이 "."(결측)인 날은 건너뛴다. API 키가 쿼리에 들어가므로 요청 URL과
 * 예외 메시지는 로그에 남기지 않는다(예외 종류만).
 */
@Component
@Order(10) // Cboe·야후보다 뒤(대체)
class FredIndicatorSource implements GlobalIndicatorSource {

    private static final Logger log = LoggerFactory.getLogger(FredIndicatorSource.class);

    static final Map<MarketIndicator, String> SERIES = Map.of(
            MarketIndicator.NASDAQ, "NASDAQCOM",
            MarketIndicator.SP500, "SP500",
            MarketIndicator.DOW, "DJIA",
            MarketIndicator.WTI, "DCOILWTICO",
            MarketIndicator.VIX, "VIXCLS");

    private static final String MISSING = ".";

    private final RestClient restClient;
    private final FredProperties properties;

    FredIndicatorSource(RestClient.Builder builder, FredProperties properties) {
        this.properties = properties;
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<DailyObservation> latest(MarketIndicator indicator) {
        String series = SERIES.get(indicator);
        if (series == null || !properties.enabled()) {
            return Optional.empty();
        }
        try {
            Observations response = restClient
                    .get()
                    .uri(uri -> uri.path("/fred/series/observations")
                            .queryParam("series_id", series)
                            .queryParam("api_key", properties.apiKey())
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", 10)
                            .build())
                    .retrieve()
                    .body(Observations.class);
            return response == null ? Optional.empty() : toObservation(response.observations());
        } catch (RuntimeException e) {
            log.warn("FRED 조회 실패: series={}, type={}", series, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 최신순 관측치에서 값이 있는 첫 번째를 현재 값, 두 번째를 직전 값으로 쓴다. */
    static Optional<DailyObservation> toObservation(List<Observation> observations) {
        if (observations == null) {
            return Optional.empty();
        }
        List<Observation> valued = observations.stream()
                .filter(o -> o.value() != null && !MISSING.equals(o.value()))
                .limit(2)
                .toList();
        if (valued.isEmpty()) {
            return Optional.empty();
        }
        Observation latest = valued.getFirst();
        BigDecimal previous = valued.size() > 1 ? new BigDecimal(valued.get(1).value()) : null;
        return Optional.of(
                new DailyObservation(LocalDate.parse(latest.date()), new BigDecimal(latest.value()), previous));
    }

    record Observations(List<Observation> observations) {}

    record Observation(String date, String value) {}
}
