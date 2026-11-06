package com.stockmanagement.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** contracts/web-routes.md 보유 종목 경로 (FR-003, FR-003a, FR-005~FR-008). */
class HoldingRoutesIT extends IntegrationTestBase {

    private Fixtures f;
    private long accountId;
    private long samsung;
    private long apple;

    @BeforeEach
    void seed() {
        f = new Fixtures(jdbc);
        accountId = f.account("키움 일반");
        samsung = f.samsung();
        apple = f.apple();
    }

    private ResultActions create(long account, long security, String avgPrice, String quantity) throws Exception {
        return mockMvc.perform(post("/accounts/{id}/holdings", account)
                .param("securityId", String.valueOf(security))
                .param("avgPrice", avgPrice)
                .param("quantity", quantity)
                .with(owner())
                .with(csrf())
                .header("HX-Request", "true"));
    }

    @Test
    void createsKoreanHolding() throws Exception {
        create(accountId, samsung, "71500", "10")
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("₩71,500")))
                .andExpect(content().string(containsString("10주")));
    }

    @Test
    void createsUsHoldingWithFractions() throws Exception {
        create(accountId, apple, "185.20", "0.35")
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("$185.2000")))
                .andExpect(content().string(containsString("0.35주")));
        assertThat(jdbc.sql("SELECT avg_price FROM holding")
                        .query(BigDecimal.class)
                        .single())
                .isEqualByComparingTo("185.20");
    }

    @Test
    void rejectsZeroAvgPrice() throws Exception {
        create(accountId, samsung, "0", "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().exists("HX-Retarget"))
                .andExpect(content().string(containsString("평단가는 0보다 커야 합니다")));
    }

    @Test
    void rejectsZeroQuantity() throws Exception {
        create(accountId, samsung, "71500", "0")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("수량은 0보다 커야 합니다")));
    }

    @Test
    void rejectsKrwFraction() throws Exception {
        create(accountId, samsung, "71500.5", "10").andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsNonNumericInput() throws Exception {
        create(accountId, samsung, "abc", "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("숫자를 입력해 주세요")));
    }

    @Test
    void rejectsMissingSecurity() throws Exception {
        mockMvc.perform(post("/accounts/{id}/holdings", accountId)
                        .param("securityId", "")
                        .param("avgPrice", "100")
                        .param("quantity", "1")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("종목을 검색해 선택해 주세요")));
    }

    @Test
    void rejectsDuplicateWithGuidanceAndEditLink() throws Exception {
        long existing = f.holding(accountId, samsung, "70000", "5");
        create(accountId, samsung, "71500", "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("이미 등록된 종목입니다. 기존 항목을 수정하세요")))
                .andExpect(content().string(containsString("/holdings/" + existing + "/edit")));
    }

    @Test
    void sameSecurityInAnotherAccountIsAllowed() throws Exception {
        f.holding(accountId, samsung, "70000", "5");
        long other = f.account("미래에셋");
        create(other, samsung, "71500", "10").andExpect(status().isCreated());
    }

    @Test
    void rejectsWhenHoldingLimitReached() throws Exception {
        f.manyHoldings(accountId, 20);
        create(accountId, apple, "185", "1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("보유 종목은 최대 20개까지 등록할 수 있습니다")));
        assertThat(jdbc.sql("SELECT count(*) FROM holding").query(Long.class).single())
                .isEqualTo(20);
    }

    @Test
    void createInDeletedAccountIs404() throws Exception {
        jdbc.sql("UPDATE account SET deleted_at = now()").update();
        create(accountId, samsung, "71500", "10").andExpect(status().isNotFound());
    }

    @Test
    void createWithUnknownSecurityIs422() throws Exception {
        create(accountId, 99999, "100", "1").andExpect(status().isUnprocessableContent());
    }

    @Test
    void updatesHolding() throws Exception {
        long id = f.holding(accountId, samsung, "70000", "5");
        mockMvc.perform(put("/holdings/{id}", id)
                        .param("avgPrice", "72000")
                        .param("quantity", "7")
                        .with(owner())
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("₩72,000")));
    }

    @Test
    void updateValidationFails() throws Exception {
        long id = f.holding(accountId, samsung, "70000", "5");
        mockMvc.perform(put("/holdings/{id}", id)
                        .param("avgPrice", "-1")
                        .param("quantity", "7")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void updateMissingIs404() throws Exception {
        mockMvc.perform(put("/holdings/{id}", 999)
                        .param("avgPrice", "1")
                        .param("quantity", "1")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void editFormAndRowFragments() throws Exception {
        long id = f.holding(accountId, samsung, "70000", "5");
        mockMvc.perform(get("/holdings/{id}/edit", id).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"70000\"")));
        mockMvc.perform(get("/holdings/{id}", id).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("₩70,000")));
        mockMvc.perform(get("/holdings/{id}/edit", 999).with(owner())).andExpect(status().isNotFound());
    }

    @Test
    void deletesHolding() throws Exception {
        long id = f.holding(accountId, samsung, "70000", "5");
        mockMvc.perform(delete("/holdings/{id}", id).with(owner()).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM holding").query(Long.class).single())
                .isZero();
    }

    @Test
    void deleteMissingIs404() throws Exception {
        mockMvc.perform(delete("/holdings/{id}", 999).with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        createOwner();
        mockMvc.perform(post("/accounts/{id}/holdings", accountId)
                        .param("securityId", String.valueOf(samsung))
                        .param("avgPrice", "1")
                        .param("quantity", "1")
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rowShowsLetterAvatarAndMarketFlag() throws Exception {
        create(accountId, samsung, "71500", "10")
                .andExpect(content().string(containsString("security-avatar tone-")))
                .andExpect(content().string(containsString(">삼</span>")))
                .andExpect(content().string(containsString("/img/flag-kr.svg")))
                .andExpect(content().string(containsString("alt=\"한국 주식\"")));
        create(accountId, apple, "185.20", "1")
                .andExpect(content().string(containsString(">애</span>")))
                .andExpect(content().string(containsString("/img/flag-us.svg")))
                .andExpect(content().string(containsString("alt=\"미국 주식\"")));
    }

    @Test
    void acceptsThousandsSeparatedAvgPrice() throws Exception {
        create(accountId, samsung, "1,234,567", "1")
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("₩1,234,567")));
    }

    @Test
    void rowActionsAreIconButtonsAndAvgPriceFieldsFormatThousands() throws Exception {
        long holding = f.holding(accountId, samsung, "71500", "10");
        String row = mockMvc.perform(
                        get("/holdings/{id}", holding).with(owner()).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(row).contains("aria-label=\"삼성전자 가이드 기록 보기\"", "aria-label=\"삼성전자 수정\"", "aria-label=\"삼성전자 삭제\"");
        assertThat(row.split("class=\"btn-icon\"", -1)).hasSize(5); // 가이드·기록·수정·삭제
        mockMvc.perform(get("/holdings/{id}/edit", holding).with(owner()).header("HX-Request", "true"))
                .andExpect(content().string(containsString("data-thousands=\"true\"")));
        mockMvc.perform(get("/").with(owner())).andExpect(content().string(containsString("data-thousands=\"true\"")));
    }

    @Test
    void flagImagesArePublicSvg() throws Exception {
        for (String flag : new String[] {"kr", "us"}) {
            mockMvc.perform(get("/img/flag-{f}.svg", flag))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("image/svg+xml")));
        }
    }

    @Test
    void requiresCsrf() throws Exception {
        mockMvc.perform(post("/accounts/{id}/holdings", accountId)
                        .param("securityId", String.valueOf(samsung))
                        .param("avgPrice", "1")
                        .param("quantity", "1")
                        .with(owner()))
                .andExpect(status().isForbidden());
    }
}
