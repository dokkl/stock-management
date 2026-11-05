package com.stockmanagement.market;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.stockmanagement.market.application.SecurityMasterSync;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 토스증권 GET /api/v1/stocks/all 기반 종목 마스터 동기화: 신규, 이름 변경, 누락 DELISTED, 멱등, 토큰 재발급. */
class SecurityMasterSyncIT extends IntegrationTestBase {

    @Autowired
    private SecurityMasterSync sync;

    @BeforeEach
    void stubTokenAndEmptyMarkets() {
        WIREMOCK.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        for (String market : new String[] {"KOSDAQ", "NYSE", "AMEX"}) {
            stubMarket(market, "[]");
        }
    }

    @Test
    void insertsRenamesAndDelistsSecurities() {
        Fixtures f = new Fixtures(jdbc);
        f.security("KOSPI", "000001", "사라질종목", null);
        f.security("KOSPI", "005930", "옛이름", null);
        stubMarket(
                "KOSPI",
                "[" + stock("005930", "삼성전자", "STOCK") + "," + stock("000660", "SK하이닉스", "STOCK") + ","
                        + stock("00066W", "신주인수권", "STOCK_WARRANTS") + "]");
        stubMarket("NASDAQ", "[" + stock("AAPL", "애플", "FOREIGN_STOCK") + "]");

        sync.syncAll();

        assertThat(name("KOSPI", "005930")).isEqualTo("삼성전자");
        assertThat(name("KOSPI", "000660")).isEqualTo("SK하이닉스");
        assertThat(status("KOSPI", "000001")).isEqualTo("DELISTED");
        assertThat(name("NASDAQ", "AAPL")).isEqualTo("애플");
        assertThat(jdbc.sql("SELECT count(*) FROM security WHERE symbol = '00066W'")
                        .query(Long.class)
                        .single())
                .as("신주인수권은 보유 종목 대상이 아니므로 제외")
                .isZero();
        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/stocks/all"))
                .withQueryParam("market", equalTo("KOSPI"))
                .withQueryParam("status", equalTo("ACTIVE"))
                .withHeader("Authorization", equalTo("Bearer tok")));

        sync.syncAll();
        assertThat(jdbc.sql("SELECT count(*) FROM security WHERE exchange = 'KOSPI'")
                        .query(Long.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void failedDownloadDoesNotDelistExchange() {
        new Fixtures(jdbc).security("KOSPI", "005930", "삼성전자", null);
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/all"))
                .withQueryParam("market", equalTo("KOSPI"))
                .willReturn(aResponse().withStatus(500)));
        stubMarket("NASDAQ", "[]");

        sync.syncAll();

        assertThat(status("KOSPI", "005930")).isEqualTo("ACTIVE");
    }

    @Test
    void relistedSecurityBecomesActiveAgain() {
        new Fixtures(jdbc).security("KOSPI", "005930", "삼성전자", null, "DELISTED");
        stubMarket("KOSPI", "[" + stock("005930", "삼성전자", "STOCK") + "]");
        stubMarket("NASDAQ", "[]");

        sync.syncAll();

        assertThat(status("KOSPI", "005930")).isEqualTo("ACTIVE");
    }

    @Test
    void expiredTokenIsReissuedOnce() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/all"))
                .withQueryParam("market", equalTo("KOSPI"))
                .inScenario("expired")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"expired-token\",\"message\":\"\"}}"))
                .willSetStateTo("reissued"));
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/all"))
                .withQueryParam("market", equalTo("KOSPI"))
                .inScenario("expired")
                .whenScenarioStateIs("reissued")
                .willReturn(json("{\"result\":[" + stock("005930", "삼성전자", "STOCK") + "]}")));
        stubMarket("NASDAQ", "[]");

        sync.syncAll();

        assertThat(name("KOSPI", "005930")).isEqualTo("삼성전자");
    }

    private String name(String exchange, String symbol) {
        return jdbc.sql("SELECT name_ko FROM security WHERE exchange = :e AND symbol = :s")
                .param("e", exchange)
                .param("s", symbol)
                .query(String.class)
                .single();
    }

    private String status(String exchange, String symbol) {
        return jdbc.sql("SELECT trading_status FROM security WHERE exchange = :e AND symbol = :s")
                .param("e", exchange)
                .param("s", symbol)
                .query(String.class)
                .single();
    }

    private static String stock(String symbol, String name, String type) {
        return "{\"symbol\":\"" + symbol + "\",\"name\":\"" + name + "\",\"securityType\":\"" + type
                + "\",\"isCommonShare\":true,\"isinCode\":\"KR7" + symbol + "\"}";
    }

    private static void stubMarket(String market, String resultArray) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/stocks/all"))
                .withQueryParam("market", equalTo(market))
                .willReturn(json("{\"result\":" + resultArray + "}")));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
