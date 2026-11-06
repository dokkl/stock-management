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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** contracts/web-routes.md AI 설정 경로: 등록·교체·검증 실패·일시 장애·선택·삭제, 인증·CSRF. */
class AiSettingsRoutesIT extends IntegrationTestBase {

    private static final String CLAUDE_KEY = "sk-ant-api03-valid-key-abcd";
    private static final String CLAUDE_KEY_2 = "sk-ant-api03-other-key-wxyz";
    private static final String OPENAI_KEY = "sk-proj-valid-openai-key-9876";

    private final AiStubs ai = new AiStubs(WIREMOCK);

    @BeforeEach
    void stubProviders() {
        ai.rejectUnknownKeys();
        ai.anthropicAccepts(CLAUDE_KEY);
        ai.anthropicAccepts(CLAUDE_KEY_2);
        ai.openAiAccepts(OPENAI_KEY);
    }

    private ResultActions register(String provider, String key) throws Exception {
        return mockMvc.perform(put("/settings/ai/credentials/" + provider)
                .param("apiKey", key)
                .with(owner())
                .with(csrf())
                .header("HX-Request", "true"));
    }

    private ResultActions select(String provider) throws Exception {
        return mockMvc.perform(put("/settings/ai/selection")
                .param("provider", provider)
                .with(owner())
                .with(csrf())
                .header("HX-Request", "true"));
    }

    private String selected() {
        return jdbc.sql("SELECT selected_ai_provider FROM app_user")
                .query(String.class)
                .single();
    }

    @Test
    void settingsPageShowsKeyRequiredForEveryProvider() throws Exception {
        mockMvc.perform(get("/settings/ai").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(allOf(containsString("Claude"), containsString("OpenAI"), containsString("키 필요"))));
    }

    @Test
    void firstValidKeyIsStoredMaskedAndAutoSelected() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY)
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(allOf(
                                containsString("****abcd"),
                                containsString("키를 확인하고 저장했습니다"),
                                not(containsString(CLAUDE_KEY)))));

        assertThat(selected()).isEqualTo("ANTHROPIC");
        assertThat(jdbc.sql("SELECT last4 FROM ai_credential")
                        .query(String.class)
                        .single())
                .isEqualTo("abcd");
    }

    @Test
    void reRegisteringSameProviderReplacesKey() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY);
        register("ANTHROPIC", CLAUDE_KEY_2)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("****wxyz")));

        assertThat(jdbc.sql("SELECT last4 FROM ai_credential")
                        .query(String.class)
                        .list())
                .containsExactly("wxyz");
    }

    @Test
    void rejectedKeyIs422WithEmptyInputAndNothingStored() throws Exception {
        String wrong = "sk-ant-api03-wrong-key-0000";
        register("ANTHROPIC", wrong)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content()
                        .string(allOf(
                                containsString("키를 확인할 수 없습니다"),
                                containsString("value=\"\""),
                                not(containsString(wrong)))));

        assertThat(jdbc.sql("SELECT count(*) FROM ai_credential")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void blankKeyIs422() throws Exception {
        register("OPENAI", "  ")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("API 키를 입력해 주세요")));
    }

    @Test
    void providerOutageIs503AndNothingStored() throws Exception {
        WIREMOCK.resetAll();
        ai.anthropicResponds(503);

        register("ANTHROPIC", CLAUDE_KEY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("잠시 후 다시 시도해 주세요")));

        assertThat(jdbc.sql("SELECT count(*) FROM ai_credential")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void secondKeyDoesNotChangeSelectionUntilSelected() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY);
        register("OPENAI", OPENAI_KEY);
        assertThat(selected()).isEqualTo("ANTHROPIC");

        select("OPENAI").andExpect(status().isOk()).andExpect(content().string(containsString("공급자를 저장했습니다")));
        assertThat(selected()).isEqualTo("OPENAI");
    }

    @Test
    void selectingProviderWithoutKeyIs422() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY);

        select("OPENAI")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("키가 등록된 공급자만 선택할 수 있습니다")));
        select("UNKNOWN").andExpect(status().isUnprocessableContent());
        assertThat(selected()).isEqualTo("ANTHROPIC");
    }

    @Test
    void deletingSelectedKeyMovesSelectionToRemainingKeyThenClearsIt() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY);
        register("OPENAI", OPENAI_KEY);

        mockMvc.perform(delete("/settings/ai/credentials/ANTHROPIC")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("키를 삭제했습니다")));
        assertThat(selected()).isEqualTo("OPENAI");

        mockMvc.perform(delete("/settings/ai/credentials/OPENAI").with(owner()).with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT selected_ai_provider FROM app_user")
                        .query(String.class)
                        .optional())
                .isEmpty();
    }

    @Test
    void deletingUnselectedKeyKeepsSelection() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY);
        register("OPENAI", OPENAI_KEY);

        mockMvc.perform(delete("/settings/ai/credentials/OPENAI").with(owner()).with(csrf()))
                .andExpect(status().isOk());

        assertThat(selected()).isEqualTo("ANTHROPIC");
    }

    @Test
    void unknownProviderIs404() throws Exception {
        register("GEMINI", CLAUDE_KEY).andExpect(status().isNotFound());
    }

    @Test
    void databaseHoldsNoPlaintextKey() throws Exception {
        register("ANTHROPIC", CLAUDE_KEY);

        List<byte[]> ciphertexts = jdbc.sql("SELECT ciphertext FROM ai_credential")
                .query(byte[].class)
                .list();
        assertThat(new String(ciphertexts.getFirst(), StandardCharsets.ISO_8859_1))
                .doesNotContain(CLAUDE_KEY);
        assertThat(jdbc.sql(
                                "SELECT count(*) FROM ai_credential WHERE position(convert_to(:key, 'UTF8') in ciphertext) > 0")
                        .param("key", CLAUDE_KEY)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void unauthenticatedHtmxIs401() throws Exception {
        createOwner();
        mockMvc.perform(put("/settings/ai/credentials/ANTHROPIC")
                        .param("apiKey", CLAUDE_KEY)
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCsrfIs403() throws Exception {
        mockMvc.perform(put("/settings/ai/credentials/ANTHROPIC")
                        .param("apiKey", CLAUDE_KEY)
                        .with(owner()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT count(*) FROM ai_credential")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
