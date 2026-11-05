package com.stockmanagement.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stockmanagement.aicredential.application.CredentialCipher;
import com.stockmanagement.aicredential.application.port.CredentialStore;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 가이드 통합 테스트 준비: 계좌·보유 종목·시세, 저장된 AI 키, 토스증권 일봉 대역, 비동기 완료 대기. */
public final class GuideFixture {

    public static final String OPENAI_KEY = "sk-proj-guide-test-key-7777";

    private final JdbcClient jdbc;
    private final Fixtures fixtures;

    public long account;
    public long samsung;
    public long holding;

    public GuideFixture(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.fixtures = new Fixtures(jdbc);
    }

    /** 키움 일반 계좌에 삼성전자 10주(평단가 71,500), 현재가 71,000 시세. */
    public GuideFixture samsungHolding(Instant quoteFetchedAt) {
        account = fixtures.account("키움 일반");
        samsung = fixtures.samsung();
        holding = fixtures.holding(account, samsung, "71500", "10");
        fixtures.quote(samsung, "71000", "70000", "OPEN", quoteFetchedAt);
        return this;
    }

    public Fixtures fixtures() {
        return fixtures;
    }

    /** OpenAI 키를 저장하고 선택한다(소유자 행이 있어야 한다). */
    public static void storeOpenAiKey(
            CredentialStore store,
            CredentialCipher cipher,
            Instant now,
            com.stockmanagement.user.domain.UserId userId) {
        store.save(userId, AiProvider.OPENAI, cipher.encrypt(ApiKey.of(OPENAI_KEY)), now);
        store.select(userId, AiProvider.OPENAI);
    }

    /** 토큰, 일봉(최근 3일, 거래량 포함), 국내 투자자별 매매동향·공매도 대역. */
    public static void stubMarket(WireMockServer server) {
        server.stubFor(get(urlPathEqualTo("/api/v1/stocks/005930/investor-trading"))
                .willReturn(json("{\"result\":{\"records\":["
                        + "{\"date\":\"2026-11-04\",\"individual\":{\"netBuyVolume\":\"-1200\"},"
                        + "\"foreigner\":{\"netBuyVolume\":\"1500\"},\"institution\":{\"netBuyVolume\":\"-300\"},"
                        + "\"foreignerHolding\":{\"holdingRate\":\"0.5089\"}}],\"nextUntil\":null}}")));
        server.stubFor(get(urlPathEqualTo("/api/v1/stocks/005930/short-selling"))
                .willReturn(json("{\"result\":{\"records\":[{\"date\":\"2026-11-04\","
                        + "\"shortSellingVolumeRate\":\"0.03120\"}],\"nextUntil\":null}}")));
        server.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(json("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        server.stubFor(
                get(urlPathEqualTo("/api/v1/candles"))
                        .willReturn(
                                json(
                                        "{\"result\":{\"candles\":["
                                                + "{\"timestamp\":\"2026-11-04T00:00:00+09:00\",\"closePrice\":\"70000\",\"volume\":\"9000\"},"
                                                + "{\"timestamp\":\"2026-11-03T00:00:00+09:00\",\"closePrice\":\"69500\"},"
                                                + "{\"timestamp\":\"2026-11-02T00:00:00+09:00\",\"closePrice\":\"69000\"}],\"nextBefore\":null}}")));
    }

    /** 가이드 요청이 PENDING을 벗어날 때까지 기다리고 최종 상태를 돌려준다. */
    public String awaitTerminal(long guideId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String status = status(guideId);
            if (!"PENDING".equals(status)) {
                return status;
            }
            sleep();
        }
        throw new AssertionError("가이드가 끝나지 않았습니다: id=" + guideId);
    }

    /** 실행 중인 모든 가이드가 끝날 때까지 기다린다(테스트 정리 전에 백그라운드 작업을 멈추기 위해). */
    public void awaitNoPending(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (jdbc.sql("SELECT count(*) FROM guide_request WHERE status = 'PENDING'")
                            .query(Long.class)
                            .single()
                    == 0) {
                return;
            }
            sleep();
        }
        throw new AssertionError("진행 중인 가이드가 남아 있습니다");
    }

    public String status(long guideId) {
        return jdbc.sql("SELECT status FROM guide_request WHERE id = :id")
                .param("id", guideId)
                .query(String.class)
                .single();
    }

    public long latestGuideId() {
        return jdbc.sql("SELECT max(id) FROM guide_request").query(Long.class).single();
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
