package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.stockmanagement.market.application.port.MarketDataProvider;
import com.stockmanagement.market.domain.InvestorFlow;
import com.stockmanagement.market.domain.ShortSellingDay;
import com.stockmanagement.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 국내 종목 수급 데이터: 투자자별 매매동향(잠정치 null 포함), 공매도 비중, 최신순, 조회 일수 상한. */
class TossTradingTrendIT extends IntegrationTestBase {

    @Autowired
    private MarketDataProvider provider;

    @BeforeEach
    void stubToken() {
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void parsesInvestorFlowsNewestFirstWithProvisionalNulls() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/005930/investor-trading"))
                .withQueryParam("count", equalTo("20"))
                .willReturn(json("{\"result\":{\"records\":["
                        + "{\"date\":\"2026-11-03\",\"individual\":{\"netBuyVolume\":\"-100\"},"
                        + "\"foreigner\":{\"netBuyVolume\":\"200\"},\"institution\":{\"netBuyVolume\":\"-50\"},"
                        + "\"foreignerHolding\":{\"holdingRate\":\"0.5080\"}},"
                        + "{\"date\":\"2026-11-04\",\"individual\":null,"
                        + "\"foreigner\":{\"netBuyVolume\":\"1500\"},\"institution\":{\"netBuyVolume\":\"-300\"},"
                        + "\"foreignerHolding\":null}],\"nextUntil\":null}}")));

        List<InvestorFlow> flows = provider.fetchInvestorFlows("005930", 20);

        assertThat(flows)
                .extracting(InvestorFlow::date)
                .containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3));
        assertThat(flows.getFirst().foreignNet()).isEqualByComparingTo("1500");
        assertThat(flows.getFirst().individualNet()).isNull();
        assertThat(flows.getFirst().foreignHoldingRate()).isNull();
        assertThat(flows.get(1).foreignHoldingRate().toPlainString()).isEqualTo("0.5080");
    }

    @Test
    void parsesShortSellingRates() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/005930/short-selling"))
                .willReturn(json("{\"result\":{\"records\":["
                        + "{\"date\":\"2026-11-04\",\"shortSellingVolumeRate\":\"0.03120\"},"
                        + "{\"date\":\"2026-11-03\",\"shortSellingVolumeRate\":null}],\"nextUntil\":null}}")));

        List<ShortSellingDay> days = provider.fetchShortSelling("005930", 20);

        assertThat(days.getFirst().volumeRate().toPlainString()).isEqualTo("0.03120");
        assertThat(days.get(1).volumeRate()).isNull();
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/stocks/005930/short-selling"))
                .withQueryParam("count", equalTo("20")));
    }

    @Test
    void emptyRecordsAndLimits() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/005930/investor-trading"))
                .willReturn(json("{\"result\":{\"records\":[],\"nextUntil\":null}}")));

        assertThat(provider.fetchInvestorFlows("005930", 100)).isEmpty();
        assertThatThrownBy(() -> provider.fetchInvestorFlows("005930", 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.fetchShortSelling("005930", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
