package com.stockmanagement.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.Fixtures;
import com.stockmanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** contracts/web-routes.md 계좌 경로: 성공, 검증 실패, 권한 실패, 없는 리소스. */
class AccountRoutesIT extends IntegrationTestBase {

    @Test
    void newAccountNameFieldShowsExamplePlaceholder() throws Exception {
        mockMvc.perform(get("/").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("placeholder=\"예: 토스증권\"")));
    }

    @Test
    void dashboardShowsCopyrightFooter() throws Exception {
        mockMvc.perform(get("/").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<footer class=\"site-footer\"")))
                .andExpect(content().string(containsString("ⓒ 2026. hoon All rights reserved.")));
    }

    @Test
    void createsAccount() throws Exception {
        mockMvc.perform(post("/accounts")
                        .param("name", "키움 일반")
                        .with(owner())
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("키움 일반")));
        assertThat(jdbc.sql("SELECT name FROM account").query(String.class).single())
                .isEqualTo("키움 일반");
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/accounts")
                        .param("name", " ")
                        .with(owner())
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("계좌명을 입력해 주세요")));
    }

    @Test
    void rejectsThirtyOneCharacterName() throws Exception {
        mockMvc.perform(post("/accounts")
                        .param("name", "가".repeat(31))
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsThirdAccount() throws Exception {
        new Fixtures(jdbc).account("키움 일반");
        new Fixtures(jdbc).account("미래에셋 해외");

        mockMvc.perform(post("/accounts").param("name", "세 번째").with(owner()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("계좌는 최대 2개까지 만들 수 있습니다")));
        assertThat(jdbc.sql("SELECT count(*) FROM account").query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void deletedAccountFreesSlot() throws Exception {
        long deleted = new Fixtures(jdbc).account("삭제할 계좌");
        new Fixtures(jdbc).account("미래에셋 해외");
        jdbc.sql("UPDATE account SET deleted_at = now() WHERE id = :id")
                .param("id", deleted)
                .update();

        mockMvc.perform(post("/accounts").param("name", "새 계좌").with(owner()).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateName() throws Exception {
        new Fixtures(jdbc).account("키움 일반");
        mockMvc.perform(post("/accounts").param("name", "키움 일반").with(owner()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("이미 있는 계좌명")));
    }

    @Test
    void allowsNameOfDeletedAccount() throws Exception {
        long id = new Fixtures(jdbc).account("키움 일반");
        jdbc.sql("UPDATE account SET deleted_at = now() WHERE id = :id")
                .param("id", id)
                .update();
        mockMvc.perform(post("/accounts").param("name", "키움 일반").with(owner()).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void renamesAccount() throws Exception {
        long id = new Fixtures(jdbc).account("키움 일반");
        mockMvc.perform(put("/accounts/{id}", id)
                        .param("name", "키움 ISA")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("키움 ISA")));
    }

    @Test
    void renameValidationFails() throws Exception {
        long id = new Fixtures(jdbc).account("키움 일반");
        mockMvc.perform(put("/accounts/{id}", id)
                        .param("name", "")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void renameMissingAccountIs404() throws Exception {
        mockMvc.perform(put("/accounts/{id}", 999)
                        .param("name", "x")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSoftDeletesAccountAndRemovesHoldings() throws Exception {
        Fixtures f = new Fixtures(jdbc);
        long id = f.account("키움 일반");
        f.holding(id, f.samsung(), "71500", "10");
        mockMvc.perform(delete("/accounts/{id}", id).with(owner()).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT deleted_at IS NOT NULL FROM account WHERE id = :id")
                        .param("id", id)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM holding").query(Long.class).single())
                .isZero();
    }

    @Test
    void deleteMissingAccountIs404() throws Exception {
        mockMvc.perform(delete("/accounts/{id}", 999).with(owner()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAlreadyDeletedAccountIs404() throws Exception {
        long id = new Fixtures(jdbc).account("키움 일반");
        jdbc.sql("UPDATE account SET deleted_at = now() WHERE id = :id")
                .param("id", id)
                .update();
        mockMvc.perform(delete("/accounts/{id}", id).with(owner()).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        createOwner();
        mockMvc.perform(post("/accounts").param("name", "x").with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrf() throws Exception {
        mockMvc.perform(post("/accounts").param("name", "x").with(owner())).andExpect(status().isForbidden());
    }
}
