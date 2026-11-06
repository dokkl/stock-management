package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.market.application.DailyObservation;
import com.stockmanagement.market.application.port.GlobalIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 미국 지표 최신 종가 제공처(003 research R3 개정): Cboe 일별 CSV(S&P 500·VIX), 야후 선물(WTI). */
class LatestCloseSourcesIT extends IntegrationTestBase {

    @Autowired
    private List<GlobalIndicatorSource> sources;

    private Optional<DailyObservation> from(String simpleName, MarketIndicator indicator) {
        return sources.stream()
                .filter(s -> s.getClass().getSimpleName().startsWith(simpleName))
                .findFirst()
                .orElseThrow()
                .latest(indicator);
    }

    @Test
    void cboeCsvLastTwoRowsGiveLatestAndPrevious() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/global/us_indices/daily_prices/SPX_History.csv"))
                .willReturn(aResponse()
                        .withBody("DATE,SPX\n01/02/1975,70.23\n11/04/2026,7688.620000\n11/05/2026,7353.730000\n")));
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/api/global/us_indices/daily_prices/VIX_History.csv"))
                        .willReturn(
                                aResponse()
                                        .withBody(
                                                "DATE,OPEN,HIGH,LOW,CLOSE\n11/04/2026,19.2,19.22,16.57,16.610000\n11/05/2026,17.14,28.36,16.7,28.180000\n\n")));

        assertThat(from("Cboe", MarketIndicator.SP500)).hasValueSatisfying(o -> {
            assertThat(o.date()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(o.value()).isEqualByComparingTo("7353.73");
            assertThat(o.previousValue()).isEqualByComparingTo("7688.62");
        });
        assertThat(from("Cboe", MarketIndicator.VIX))
                .hasValueSatisfying(o -> assertThat(o.value()).isEqualByComparingTo("28.18"));
        assertThat(from("Cboe", MarketIndicator.NASDAQ)).as("Cboe 대상 아님").isEmpty();
    }

    @Test
    void yahooFuturesUsesLastTwoNonNullDailyCloses() {
        WIREMOCK.stubFor(get(urlPathMatching("/v8/finance/chart/CL(=|%3D)F"))
                .withQueryParam("interval", equalTo("1d"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {"chart":{"result":[{"meta":{"exchangeTimezoneName":"America/New_York"},
                          "timestamp":[1793768400,1793854800,1793941200],
                          "indicators":{"quote":[{"close":[88.12000274658203,90.5199966430664,null]}]}}]}}""")));

        assertThat(from("Yahoo", MarketIndicator.WTI)).hasValueSatisfying(o -> {
            assertThat(o.date()).as("봉 시작 시각(뉴욕 자정)의 날짜").isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(o.value()).isEqualByComparingTo("90.52");
            assertThat(o.previousValue()).isEqualByComparingTo("88.12");
        });
        assertThat(from("Yahoo", MarketIndicator.VIX)).isEmpty();
    }

    @Test
    void failuresAreEmpty() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/global/us_indices/daily_prices/SPX_History.csv"))
                .willReturn(serverError()));
        WIREMOCK.stubFor(get(urlPathMatching("/v8/finance/chart/CL(=|%3D)F")).willReturn(serverError()));

        assertThat(from("Cboe", MarketIndicator.SP500)).isEmpty();
        assertThat(from("Yahoo", MarketIndicator.WTI)).isEmpty();
    }
}
