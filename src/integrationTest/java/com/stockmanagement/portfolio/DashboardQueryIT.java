package com.stockmanagement.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.common.domain.Currency;
import com.stockmanagement.portfolio.application.DashboardQuery;
import com.stockmanagement.portfolio.application.DashboardView;
import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.support.QueryCounter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 대시보드 조회: 계좌별 그룹, 통화별 평단가, 삭제 계좌 제외, N+1 금지. */
class DashboardQueryIT extends IntegrationTestBase {

    @Autowired
    private DashboardQuery dashboardQuery;

    @Autowired
    private QueryCounter queryCounter;

    @Test
    void groupsHoldingsByAccountWithCurrencies() {
        Fixtures f = new Fixtures(jdbc);
        long kiwoom = f.account("키움 일반");
        long mirae = f.account("미래에셋 해외");
        f.account("빈 계좌");
        f.holding(kiwoom, f.samsung(), "71500", "10");
        f.holding(mirae, f.apple(), "185.2", "0.35");

        DashboardView view = dashboardQuery.load();

        assertThat(view.accounts())
                .extracting(DashboardView.AccountView::name)
                .containsExactly("미래에셋 해외", "빈 계좌", "키움 일반");
        DashboardView.HoldingView apple = view.accounts().getFirst().holdings().getFirst();
        assertThat(apple.symbol()).isEqualTo("AAPL");
        assertThat(apple.currency()).isEqualTo(Currency.USD);
        assertThat(view.accounts().get(1).holdings()).isEmpty();
        assertThat(view.accounts().get(2).holdings().getFirst().currency()).isEqualTo(Currency.KRW);
        assertThat(view.totalHoldings()).isEqualTo(2);
    }

    @Test
    void excludesDeletedAccounts() {
        Fixtures f = new Fixtures(jdbc);
        long id = f.account("삭제됨");
        jdbc.sql("UPDATE account SET deleted_at = now() WHERE id = :id")
                .param("id", id)
                .update();
        assertThat(dashboardQuery.load().accounts()).isEmpty();
    }

    @Test
    void loadsFiveHundredHoldingsWithSingleQuery() {
        Fixtures f = new Fixtures(jdbc);
        long id = f.account("대량");
        f.manyHoldings(id, 500);
        queryCounter.reset();

        DashboardView view = dashboardQuery.load();

        assertThat(view.totalHoldings()).isEqualTo(500);
        assertThat(queryCounter.count()).isLessThanOrEqualTo(2);
    }

    @Test
    void dashboardPageRendersAccountsAndEmptyState() throws Exception {
        mockMvc.perform(get("/").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("계좌를 추가해 보세요")));
        Fixtures f = new Fixtures(jdbc);
        long id = f.account("키움 일반");
        f.holding(id, f.samsung(), "71500", "10");
        mockMvc.perform(get("/").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("삼성전자")))
                .andExpect(content().string(containsString("시세 없음")))
                .andExpect(content().string(not(containsString("계좌를 추가해 보세요"))));
    }
}
