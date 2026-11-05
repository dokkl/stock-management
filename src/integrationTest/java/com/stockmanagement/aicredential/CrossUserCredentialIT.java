package com.stockmanagement.aicredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.IntegrationTestBase;
import com.stockmanagement.user.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AI 키 사용자 격리(T027): 키·선택은 사용자마다 따로다. */
class CrossUserCredentialIT extends IntegrationTestBase {

    private static final String A_KEY = "sk-ant-api03-user-a-key-aaaa";
    private static final String B_KEY = "sk-ant-api03-user-b-key-bbbb";

    private final AiStubs ai = new AiStubs(WIREMOCK);
    private UserId a;
    private UserId b;

    @BeforeEach
    void setUp() {
        a = userWithKakao(6001);
        b = userWithKakao(6002);
        ai.rejectUnknownKeys();
        ai.anthropicAccepts(A_KEY);
        ai.anthropicAccepts(B_KEY);
    }

    private void register(UserId user, String key) throws Exception {
        mockMvc.perform(put("/settings/ai/credentials/ANTHROPIC")
                        .param("apiKey", key)
                        .with(as(user))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private String selected(UserId user) {
        return jdbc.sql("SELECT selected_ai_provider FROM app_user WHERE id = :id")
                .param("id", user.value())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    @Test
    void keysAndSelectionAreIndependentPerUser() throws Exception {
        register(a, A_KEY);

        mockMvc.perform(get("/settings/ai").with(as(b)))
                .andExpect(content().string(allOf(containsString("키 필요"), not(containsString("****aaaa")))));
        assertThat(selected(b)).isNull();

        register(b, B_KEY);
        assertThat(jdbc.sql("SELECT count(*) FROM ai_credential WHERE provider = 'ANTHROPIC'")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);

        mockMvc.perform(delete("/settings/ai/credentials/ANTHROPIC").with(as(b)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(selected(a)).isEqualTo("ANTHROPIC");
        mockMvc.perform(get("/settings/ai").with(as(a))).andExpect(content().string(containsString("****aaaa")));
    }
}
