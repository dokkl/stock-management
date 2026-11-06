package com.stockmanagement.market.adapter.yahoo;

import com.stockmanagement.market.application.DailyObservation;
import com.stockmanagement.market.application.port.GlobalIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * WTI 근월물 선물(CL=F) 일별 종가(야후 파이낸스 차트 API, 비공식). 미국 에너지청 현물가(FRED)는 1~2일 늦어 최신 값을
 * 위해 쓴다. 실패하면 비어 있고, 갱신기가 FRED 값으로 대체한다.
 */
@Component
@Order(2)
class YahooIndicatorSource implements GlobalIndicatorSource {

    private static final Logger log = LoggerFactory.getLogger(YahooIndicatorSource.class);
    private static final String WTI_FUTURES = "CL=F";
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final RestClient restClient;

    YahooIndicatorSource(RestClient.Builder builder, YahooProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; stock-management)")
                .build();
    }

    @Override
    public Optional<DailyObservation> latest(MarketIndicator indicator) {
        if (indicator != MarketIndicator.WTI) {
            return Optional.empty();
        }
        try {
            Chart chart = restClient
                    .get()
                    .uri(uri -> uri.path("/v8/finance/chart/{symbol}")
                            .queryParam("range", "10d")
                            .queryParam("interval", "1d")
                            .build(WTI_FUTURES))
                    .retrieve()
                    .body(Chart.class);
            return toObservation(chart);
        } catch (RuntimeException e) {
            log.warn("야후 WTI 조회 실패: type={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 종가가 null이 아닌 마지막 두 봉을 최신·직전 값으로 쓴다. 날짜는 뉴욕 기준. */
    static Optional<DailyObservation> toObservation(Chart chart) {
        Optional<Result> result = firstResult(chart);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        List<Long> times = result.get().timestamp();
        List<Double> closes = result.get().indicators().quote().getFirst().close();
        int latest = lastNonNull(closes, Math.min(times.size(), closes.size()) - 1);
        if (latest < 0) {
            return Optional.empty();
        }
        int previous = lastNonNull(closes, latest - 1);
        LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(times.get(latest)), NEW_YORK);
        return Optional.of(new DailyObservation(
                date, price(closes.get(latest)), previous < 0 ? null : price(closes.get(previous))));
    }

    /** 응답에 시각·종가 배열이 모두 있는 첫 결과. */
    private static Optional<Result> firstResult(Chart chart) {
        return Optional.ofNullable(chart)
                .map(Chart::chart)
                .map(ChartBody::result)
                .filter(results -> !results.isEmpty())
                .map(List::getFirst)
                .filter(r -> r.timestamp() != null && r.indicators() != null)
                .filter(r -> r.indicators().quote() != null
                        && !r.indicators().quote().isEmpty())
                .filter(r -> r.indicators().quote().getFirst().close() != null);
    }

    private static int lastNonNull(List<Double> values, int from) {
        for (int i = from; i >= 0; i--) {
            if (values.get(i) != null) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal price(Double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    record Chart(ChartBody chart) {}

    record ChartBody(List<Result> result) {}

    record Result(List<Long> timestamp, Indicators indicators) {}

    record Indicators(List<Quote> quote) {}

    record Quote(List<Double> close) {}
}
