package com.stockmanagement.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class AuthFlowIT extends IntegrationTestBase {

    @Test
    void withoutOwnerEverythingRedirectsToSetup() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/setup"));
        mockMvc.perform(get("/login")).andExpect(status().isFound()).andExpect(redirectedUrl("/setup"));
    }

    @Test
    void setupPageIsShownWithoutOwner() throws Exception {
        mockMvc.perform(get("/setup"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"passwordConfirm\"")));
    }

    @Test
    void setupRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/setup").with(csrf()).param("password", "short").param("passwordConfirm", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("12자 이상")));
        assertNoOwner();
    }

    @Test
    void setupRejectsMismatch() throws Exception {
        mockMvc.perform(post("/setup")
                        .with(csrf())
                        .param("password", "correct-horse-battery")
                        .param("passwordConfirm", "correct-horse-batterx"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("일치하지 않습니다")));
        assertNoOwner();
    }

    @Test
    void setupSucceedsOnceThenReturns404() throws Exception {
        mockMvc.perform(post("/setup")
                        .with(csrf())
                        .param("password", OWNER_PASSWORD)
                        .param("passwordConfirm", OWNER_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/setup")).andExpect(status().isNotFound());
        mockMvc.perform(post("/setup")
                        .with(csrf())
                        .param("password", OWNER_PASSWORD)
                        .param("passwordConfirm", OWNER_PASSWORD))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        createOwner();
        mockMvc.perform(formLogin("/login").user("owner").password(OWNER_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        createOwner();
        mockMvc.perform(formLogin("/login").user("owner").password("wrong-password-xx"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void fiveFailuresLock() throws Exception {
        createOwner();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(formLogin("/login").user("owner").password("wrong-password-xx"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mockMvc.perform(formLogin("/login").user("owner").password("wrong-password-xx"))
                .andExpect(redirectedUrl("/login?locked"));
        mockMvc.perform(formLogin("/login").user("owner").password(OWNER_PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
        mockMvc.perform(get("/login").param("locked", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("15분")));
    }

    @Test
    void unauthenticatedPageRedirectsToLogin() throws Exception {
        createOwner();
        mockMvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void unauthenticatedHtmxRequestGets401WithHxRedirect() throws Exception {
        createOwner();
        mockMvc.perform(get("/").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("HX-Redirect", "/login"));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/setup").param("password", OWNER_PASSWORD).param("passwordConfirm", OWNER_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedOwnerSeesDashboard() throws Exception {
        mockMvc.perform(get("/").with(owner())).andExpect(status().isOk());
    }

    private void assertNoOwner() {
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.sql("SELECT count(*) FROM owner").query(Long.class).single())
                .isZero();
    }
}
