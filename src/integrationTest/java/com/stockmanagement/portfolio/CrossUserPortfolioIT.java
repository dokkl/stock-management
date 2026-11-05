package com.stockmanagement.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.user.domain.UserId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 포트폴리오 사용자 격리(T026, FR-008~FR-010, SC-003): 남의 계좌·보유 종목은 없는 항목과 같은 404, 데이터 불변. */
class CrossUserPortfolioIT extends IntegrationTestBase {

    private UserId a;
    private UserId b;
    private long aAccount;
    private long aHolding;
    private long samsung;

    @BeforeEach
    void setUp() {
        Fixtures f = new Fixtures(jdbc);
        a = userWithKakao(5001);
        b = userWithKakao(5002);
        samsung = f.samsung();
        aAccount = f.account(a.value(), "키움 일반");
        aHolding = f.holding(aAccount, samsung, "71500", "10");
    }

    private Map<String, Object> aSnapshot() {
        return jdbc.sql("""
                        SELECT a.name, a.deleted_at, h.avg_price, h.quantity
                        FROM account a LEFT JOIN holding h ON h.account_id = a.id WHERE a.id = :id
                        """).param("id", aAccount).query().singleRow();
    }

    private void expectSameAsMissing(MockHttpServletRequestBuilder foreign, MockHttpServletRequestBuilder missing)
            throws Exception {
        String foreignBody = mockMvc.perform(foreign.with(as(b)).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missingBody = mockMvc.perform(missing.with(as(b)).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(foreignBody).as("남의 항목과 없는 항목의 응답이 같다").isEqualTo(missingBody);
    }

    @Test
    void foreignAccountRoutesAre404AndChangeNothing() throws Exception {
        Map<String, Object> before = aSnapshot();

        expectSameAsMissing(
                put("/accounts/" + aAccount).param("name", "탈취"),
                put("/accounts/99999").param("name", "탈취"));
        expectSameAsMissing(delete("/accounts/" + aAccount), delete("/accounts/99999"));
        expectSameAsMissing(get("/accounts/" + aAccount + "/edit"), get("/accounts/99999/edit"));
        expectSameAsMissing(get("/accounts/" + aAccount + "/header"), get("/accounts/99999/header"));
        expectSameAsMissing(
                post("/accounts/" + aAccount + "/holdings")
                        .param("securityId", String.valueOf(samsung))
                        .param("avgPrice", "1")
                        .param("quantity", "1"),
                post("/accounts/99999/holdings")
                        .param("securityId", String.valueOf(samsung))
                        .param("avgPrice", "1")
                        .param("quantity", "1"));

        assertThat(aSnapshot()).isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM holding").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void foreignHoldingRoutesAre404AndChangeNothing() throws Exception {
        Map<String, Object> before = aSnapshot();

        expectSameAsMissing(get("/holdings/" + aHolding), get("/holdings/99999"));
        expectSameAsMissing(get("/holdings/" + aHolding + "/edit"), get("/holdings/99999/edit"));
        expectSameAsMissing(
                put("/holdings/" + aHolding).param("avgPrice", "1").param("quantity", "1"),
                put("/holdings/99999").param("avgPrice", "1").param("quantity", "1"));
        expectSameAsMissing(delete("/holdings/" + aHolding), delete("/holdings/99999"));

        assertThat(aSnapshot()).isEqualTo(before);
    }

    @Test
    void dashboardAndQuotesShowOnlyOwnData() throws Exception {
        long bAccount = new Fixtures(jdbc).account(b.value(), "B의 계좌");
        new Fixtures(jdbc).holding(bAccount, new Fixtures(jdbc).apple(), "185", "1");

        mockMvc.perform(get("/").with(as(b)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("B의 계좌"), not(containsString("키움 일반")))));
        mockMvc.perform(get("/fragments/quotes").with(as(b)).header("HX-Request", "true"))
                .andExpect(content().string(not(containsString("quote-" + aHolding + "\""))));
        mockMvc.perform(get("/").with(as(a)))
                .andExpect(content().string(allOf(containsString("키움 일반"), not(containsString("B의 계좌")))));
    }

    @Test
    void sameAccountNameIsAllowedForDifferentUsers() throws Exception {
        mockMvc.perform(post("/accounts")
                        .param("name", "키움 일반")
                        .with(as(b))
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/accounts")
                        .param("name", "키움 일반")
                        .with(as(a))
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("이미 있는 계좌명")));
    }
}
