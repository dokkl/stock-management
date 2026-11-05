package com.stockmanagement.aicredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SC-009: 등록 성공·검증 실패·일시 장애 흐름에서 키 원문이 로그, 응답 본문·헤더, DB 텍스트 컬럼 어디에도 없다.
 * 가이드 실패 흐름(키 인증 실패)에서도 같다.
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyLeakIT extends IntegrationTestBase {

    private static final String VALID = "sk-ant-api03-LEAKCHECK-valid-1111";
    private static final String REJECTED = "sk-proj-LEAKCHECK-rejected-2222";
    private static final String OUTAGE = "sk-ant-api03-LEAKCHECK-outage-3333";

    private final AiStubs ai = new AiStubs(WIREMOCK);

    @Test
    void keyNeverAppearsInLogsResponsesOrDatabase(CapturedOutput output) throws Exception {
        ai.rejectUnknownKeys();
        ai.anthropicAccepts(VALID);
        List<MvcResult> results = new ArrayList<>();

        results.add(register("ANTHROPIC", VALID));
        results.add(register("OPENAI", REJECTED));
        WIREMOCK.resetAll();
        ai.anthropicResponds(503);
        results.add(register("ANTHROPIC", OUTAGE));
        results.add(mockMvc.perform(get("/settings/ai").with(owner())).andReturn());

        for (String key : List.of(VALID, REJECTED, OUTAGE)) {
            assertThat(output.getAll()).as("로그").doesNotContain(key);
            for (MvcResult result : results) {
                assertThat(result.getResponse().getContentAsString())
                        .as("응답 본문")
                        .doesNotContain(key);
                for (String header : result.getResponse().getHeaderNames()) {
                    assertThat(String.valueOf(result.getResponse().getHeaderValues(header)))
                            .as("응답 헤더 " + header)
                            .doesNotContain(key);
                }
            }
            assertThat(textColumnsContaining(key)).as("DB 텍스트 컬럼").isZero();
        }
    }

    @Test
    void keyNeverAppearsDuringFailedGuide(CapturedOutput output) throws Exception {
        ai.anthropicAccepts(VALID);
        register("ANTHROPIC", VALID);
        com.stockmanagement.support.GuideFixture g =
                new com.stockmanagement.support.GuideFixture(jdbc).samsungHolding(clock.instant());
        WIREMOCK.resetAll();
        com.stockmanagement.support.GuideFixture.stubMarket(WIREMOCK);
        ai.anthropicResponds(401);

        MvcResult request = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/holdings/" + g.holding + "/guides")
                        .with(owner())
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andReturn();
        long guideId = g.latestGuideId();
        assertThat(g.awaitTerminal(guideId, java.time.Duration.ofSeconds(10))).isEqualTo("FAILED");
        MvcResult status = mockMvc.perform(get("/guides/" + guideId + "/status").with(owner()))
                .andReturn();

        assertThat(output.getAll()).doesNotContain(VALID);
        assertThat(request.getResponse().getContentAsString()).doesNotContain(VALID);
        assertThat(status.getResponse().getContentAsString())
                .contains("AI 키를 확인해 주세요")
                .doesNotContain(VALID);
        assertThat(textColumnsContaining(VALID)).isZero();
    }

    @Test
    void logPatternMasksKeyLikeValues(CapturedOutput output) {
        org.slf4j.LoggerFactory.getLogger(ApiKeyLeakIT.class)
                .warn("실수로 남긴 키: {}", VALID, new IllegalStateException("cause " + REJECTED));

        assertThat(output.getAll())
                .contains("실수로 남긴 키: ****")
                .doesNotContain(VALID)
                .doesNotContain(REJECTED);
    }

    private MvcResult register(String provider, String key) throws Exception {
        return mockMvc.perform(put("/settings/ai/credentials/" + provider)
                        .param("apiKey", key)
                        .with(owner())
                        .with(csrf())
                        .header("HX-Request", "true"))
                .andReturn();
    }

    /** public 스키마의 모든 텍스트 컬럼에서 키를 찾는다. */
    private long textColumnsContaining(String key) {
        List<String[]> columns = jdbc.sql("""
                        SELECT table_name, column_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND data_type IN ('text', 'character varying', 'character')
                        """)
                .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)})
                .list();
        long hits = 0;
        for (String[] c : columns) {
            hits += jdbc.sql("SELECT count(*) FROM " + c[0] + " WHERE position(:key in " + c[1] + ") > 0")
                    .param("key", key)
                    .query(Long.class)
                    .single();
        }
        return hits;
    }
}
