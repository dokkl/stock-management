package com.stockmanagement.aicredential;

import static org.assertj.core.api.Assertions.assertThat;
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

/** AI 설정은 관리자 전용이다. 관리자가 등록한 키는 공용이며, 일반 사용자에게 설정 화면은 없는 화면(404)이다. */
class AdminOnlySettingsIT extends IntegrationTestBase {

    private static final String KEY = "sk-ant-api03-admin-key-aaaa";

    private final AiStubs ai = new AiStubs(WIREMOCK);
    private UserId member;

    @BeforeEach
    void setUp() {
        member = userWithKakao(6002);
        ai.rejectUnknownKeys();
        ai.anthropicAccepts(KEY);
    }

    private long keyCount() {
        return jdbc.sql("SELECT count(*) FROM ai_credential").query(Long.class).single();
    }

    @Test
    void memberCannotSeeOrChangeSettings() throws Exception {
        mockMvc.perform(get("/settings/ai").with(as(member))).andExpect(status().isNotFound());
        mockMvc.perform(put("/settings/ai/credentials/ANTHROPIC")
                        .param("apiKey", KEY)
                        .with(as(member))
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/settings/ai/selection")
                        .param("provider", "ANTHROPIC")
                        .with(as(member))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(keyCount()).isZero();

        mockMvc.perform(put("/settings/ai/credentials/ANTHROPIC")
                        .param("apiKey", KEY)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/settings/ai/credentials/ANTHROPIC")
                        .with(as(member))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(keyCount()).as("일반 사용자는 공용 키를 지울 수 없다").isEqualTo(1);
    }

    @Test
    void navShowsAiSettingsOnlyToAdmin() throws Exception {
        mockMvc.perform(get("/").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/settings/ai\""))));
        mockMvc.perform(get("/").with(admin())).andExpect(content().string(containsString("href=\"/settings/ai\"")));
    }

    @Test
    void adminKeyIsSharedAndShownToEveryAdmin() throws Exception {
        mockMvc.perform(put("/settings/ai/credentials/ANTHROPIC")
                        .param("apiKey", KEY)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT selected_provider FROM ai_settings")
                        .query(String.class)
                        .single())
                .isEqualTo("ANTHROPIC");
        mockMvc.perform(get("/settings/ai").with(admin())).andExpect(content().string(containsString("****aaaa")));
    }
}
