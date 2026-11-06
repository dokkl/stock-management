package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.port.GlobalIndicatorSource;
import com.stockmanagement.market.domain.MarketIndicator;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** FRED 일별 관측치(003 T015): 결측(.) 건너뛰기, 실패 격리, API 키 비노출. */
@ExtendWith(OutputCaptureExtension.class)
class FredIndicatorSourceIT extends IntegrationTestBase {

    @Autowired
    private List<GlobalIndicatorSource> sources;

    private GlobalIndicatorSource source;

    @BeforeEach
    void pickFred() {
        source = sources.stream()
                .filter(s -> s.getClass().getSimpleName().startsWith("Fred"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void latestSkipsMissingValues() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/fred/series/observations"))
                        .withQueryParam("series_id", equalTo("SP500"))
                        .withQueryParam("api_key", equalTo("test-fred-key"))
                        .withQueryParam("file_type", equalTo("json"))
                        .withQueryParam("sort_order", equalTo("desc"))
                        .willReturn(
                                json(
                                        "{\"observations\":[{\"date\":\"2026-11-05\",\"value\":\".\"},"
                                                + "{\"date\":\"2026-11-04\",\"value\":\"6012.34\"},{\"date\":\"2026-11-03\",\"value\":\"5990.00\"}]}")));

        assertThat(source.latest(MarketIndicator.SP500)).hasValueSatisfying(o -> {
            assertThat(o.date()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(o.value()).isEqualByComparingTo("6012.34");
            assertThat(o.previousValue()).isEqualByComparingTo("5990.00");
        });
    }

    @Test
    void singleValueHasNoPreviousAndAllMissingIsEmpty() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/fred/series/observations"))
                        .withQueryParam("series_id", equalTo("VIXCLS"))
                        .willReturn(
                                json(
                                        "{\"observations\":[{\"date\":\"2026-11-04\",\"value\":\"15.2\"},{\"date\":\"2026-11-03\",\"value\":\".\"}]}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/fred/series/observations"))
                .withQueryParam("series_id", equalTo("DJIA"))
                .willReturn(json("{\"observations\":[{\"date\":\"2026-11-04\",\"value\":\".\"}]}")));

        assertThat(source.latest(MarketIndicator.VIX))
                .hasValueSatisfying(o -> assertThat(o.previousValue()).isNull());
        assertThat(source.latest(MarketIndicator.DOW)).isEmpty();
        assertThat(source.latest(MarketIndicator.KOSPI)).as("FRED 대상 아님").isEmpty();
    }

    @Test
    void failureIsEmptyAndKeyIsNotLogged(CapturedOutput output) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/fred/series/observations")).willReturn(serverError()));

        assertThat(source.latest(MarketIndicator.NASDAQ)).isEmpty();
        assertThat(output.getAll()).contains("FRED 조회 실패").doesNotContain("test-fred-key");
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
