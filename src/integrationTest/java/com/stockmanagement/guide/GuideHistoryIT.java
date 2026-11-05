package com.stockmanagement.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.guide.application.GuideReservation;
import com.stockmanagement.guide.application.port.GuideStore;
import com.stockmanagement.guide.domain.FailureKind;
import com.stockmanagement.guide.domain.GuideAction;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.guide.domain.GuideRequest;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 가이드 기록(T114, FR-025~FR-028): 최신순 20건, 성공만, 스냅샷 유지, 보유 종료·삭제된 계좌, 불변, 빈 상태. */
class GuideHistoryIT extends IntegrationTestBase {

    @Autowired
    private GuideStore store;

    private Fixtures f;
    private long account;
    private long samsung;
    private long holding;

    @BeforeEach
    void setUp() {
        f = new Fixtures(jdbc);
        account = f.account("키움 일반");
        samsung = f.samsung();
        holding = f.holding(account, samsung, "71500", "10");
    }

    /** 오늘(테스트 시계) 기준으로 성공한 가이드 1건을 남기고, 다음 기록을 위해 시계를 하루 넘긴다. */
    private long succeeded(String overall) {
        GuideReservation reservation = new GuideReservation(
                samsung,
                holding,
                account,
                "키움 일반",
                GuideRequest.usageDateOf(clock.instant()),
                AiProvider.ANTHROPIC,
                new BigDecimal("71000"),
                new BigDecimal("71500"),
                BigDecimal.TEN,
                new BigDecimal("-0.0070"),
                Currency.KRW,
                clock.instant());
        long id = store.reserve(reservation).getAsLong();
        GuideContent content = new GuideContent(
                "요약 " + overall, "진단", "수급", List.of(), overall, GuideAction.WATCH, "근거", List.of("위험"));
        store.markSucceeded(
                id, "claude-sonnet-5-5", content, null, clock.instant().plusSeconds(20));
        clock.advance(Duration.ofDays(1));
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions history(int page) throws Exception {
        return mockMvc.perform(get("/securities/" + samsung + "/guides")
                .param("page", String.valueOf(page))
                .with(owner()));
    }

    @Test
    void listsSucceededNewestFirstWithSnapshot() throws Exception {
        succeeded("첫 번째 판단");
        succeeded("두 번째 판단");
        long failed = store.reserve(new GuideReservation(
                        samsung,
                        holding,
                        account,
                        "키움 일반",
                        GuideRequest.usageDateOf(clock.instant()),
                        AiProvider.OPENAI,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        Currency.KRW,
                        clock.instant()))
                .getAsLong();
        store.markFailed(failed, FailureKind.TRANSIENT_ERROR, clock.instant());

        String html =
                history(0).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html.indexOf("두 번째 판단")).isLessThan(html.indexOf("첫 번째 판단"));
        assertThat(html)
                .contains(
                        "삼성전자 가이드 기록",
                        "2026-11-05 10:00",
                        "Claude · claude-sonnet-5-5",
                        "관망",
                        "₩71,000",
                        "₩71,500",
                        "10주",
                        "−0.70%",
                        "/guides/")
                .doesNotContain("OpenAI");
    }

    @Test
    void snapshotStaysAfterHoldingChanges() throws Exception {
        succeeded("판단");
        jdbc.sql("UPDATE holding SET avg_price = 60000, quantity = 3 WHERE id = :id")
                .param("id", holding)
                .update();

        history(0)
                .andExpect(content()
                        .string(allOf(
                                containsString("₩71,500"), containsString("10주"), not(containsString("₩60,000")))));
    }

    @Test
    void deletedHoldingShowsEndedAndDeletedAccountShowsLabel() throws Exception {
        succeeded("판단");
        jdbc.sql("DELETE FROM holding WHERE id = :id").param("id", holding).update();
        history(0).andExpect(content().string(allOf(containsString("보유 종료"), not(containsString("삭제된 계좌")))));

        jdbc.sql("UPDATE account SET deleted_at = now() WHERE id = :id")
                .param("id", account)
                .update();
        history(0).andExpect(content().string(allOf(containsString("삭제된 계좌"), containsString("판단"))));
    }

    @Test
    void pagesTwentyAtATime() throws Exception {
        for (int i = 1; i <= 21; i++) {
            succeeded("판단-" + String.format("%02d", i));
        }

        history(0)
                .andExpect(content()
                        .string(allOf(
                                containsString("판단-21"),
                                containsString("판단-02"),
                                not(containsString("판단-01")),
                                containsString("이전 기록"),
                                not(containsString("최근 기록")))));
        history(1)
                .andExpect(content()
                        .string(allOf(containsString("판단-01"), not(containsString("판단-02")), containsString("최근 기록"))));
    }

    @Test
    void recordsCannotBeModified() {
        long id = succeeded("판단");

        assertThatThrownBy(() -> jdbc.sql("UPDATE guide_request SET price_snapshot = 1 WHERE id = :id")
                        .param("id", id)
                        .update())
                .hasMessageContaining("is final");
    }

    @Test
    void emptyStateAndErrors() throws Exception {
        history(0).andExpect(status().isOk()).andExpect(content().string(containsString("아직 받은 가이드가 없습니다")));
        mockMvc.perform(get("/securities/99999/guides").with(owner())).andExpect(status().isNotFound());
        mockMvc.perform(get("/securities/" + samsung + "/guides").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardRowLinksToHistory() throws Exception {
        mockMvc.perform(get("/").with(owner()))
                .andExpect(content().string(containsString("href=\"/securities/" + samsung + "/guides\"")));
    }
}
