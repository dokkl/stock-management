package com.stockmanagement.market.adapter.cboe;

import com.stockmanagement.market.application.DailyObservation;
import com.stockmanagement.market.application.port.GlobalIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 * Cboe 공식 지수 일별 종가 CSV(S&P 500 `SPX`, VIX). 장 마감 당일 저녁에 올라와 FRED보다 빠르다.
 * 형식: 첫 줄 머리글, 날짜 MM/dd/yyyy, 마지막 열이 종가. 마지막 두 행을 최신·직전 값으로 쓴다.
 */
@Component
@Order(1)
class CboeIndicatorSource implements GlobalIndicatorSource {

    private static final Logger log = LoggerFactory.getLogger(CboeIndicatorSource.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final Map<MarketIndicator, String> FILES =
            Map.of(MarketIndicator.SP500, "SPX", MarketIndicator.VIX, "VIX");

    private final RestClient restClient;

    CboeIndicatorSource(RestClient.Builder builder, CboeProperties properties) {
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
        String file = FILES.get(indicator);
        if (file == null) {
            return Optional.empty();
        }
        try {
            String csv = restClient
                    .get()
                    .uri("/api/global/us_indices/daily_prices/{file}_History.csv", file)
                    .retrieve()
                    .body(String.class);
            return parse(csv);
        } catch (RuntimeException e) {
            log.warn("Cboe 조회 실패: file={}, type={}", file, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static Optional<DailyObservation> parse(String csv) {
        if (csv == null) {
            return Optional.empty();
        }
        List<String[]> rows = new ArrayList<>();
        for (String line : csv.lines().toList()) {
            String[] cols = line.trim().split(",", -1);
            if (cols.length >= 2 && cols[0].matches("\\d{2}/\\d{2}/\\d{4}")) {
                rows.add(cols);
            }
        }
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String[] last = rows.getLast();
        BigDecimal previous = rows.size() > 1 ? close(rows.get(rows.size() - 2)) : null;
        return Optional.of(new DailyObservation(LocalDate.parse(last[0], DATE), close(last), previous));
    }

    private static BigDecimal close(String[] cols) {
        return new BigDecimal(cols[cols.length - 1]).stripTrailingZeros();
    }
}
