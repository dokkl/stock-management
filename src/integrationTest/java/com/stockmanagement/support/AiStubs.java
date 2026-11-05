package com.stockmanagement.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

/** Anthropic(/v1/messages)·OpenAI(/v1/chat/completions) WireMock 대역. 키별로 응답을 다르게 줄 수 있다. */
public final class AiStubs {

    public static final String ANTHROPIC_PATH = "/v1/messages";
    public static final String OPENAI_PATH = "/v1/chat/completions";

    private static final String ANTHROPIC_OK = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[{"type":"text","text":"pong"}],"stop_reason":"max_tokens","stop_sequence":null,
             "usage":{"input_tokens":1,"output_tokens":1}}""";
    private static final String OPENAI_OK = """
            {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"gpt-5",
             "choices":[{"index":0,"message":{"role":"assistant","content":"pong"},"finish_reason":"length"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}""";

    /** 출력 스키마에 맞는 가이드 JSON. */
    public static final String GUIDE_JSON = "{\"summary\":\"최근 60일 동안 완만하게 올랐습니다.\","
            + "\"diagnosis\":\"평단가보다 0.7% 낮습니다.\",\"action\":\"HOLD\","
            + "\"rationale\":\"이동평균 위에서 움직이고 있습니다.\",\"risks\":[\"반도체 업황 변동\",\"환율\"]}";

    private final WireMockServer server;

    public AiStubs(WireMockServer server) {
        this.server = server;
    }

    public void anthropicAccepts(String key) {
        server.stubFor(post(urlPathEqualTo(ANTHROPIC_PATH))
                .withHeader("x-api-key", equalTo(key))
                .willReturn(json(200, ANTHROPIC_OK)));
    }

    public void anthropicResponds(int status) {
        server.stubFor(post(urlPathEqualTo(ANTHROPIC_PATH)).atPriority(10).willReturn(json(status, error(status))));
    }

    public void anthropicDelays(int millis) {
        server.stubFor(post(urlPathEqualTo(ANTHROPIC_PATH))
                .willReturn(json(200, ANTHROPIC_OK).withFixedDelay(millis)));
    }

    public void openAiAccepts(String key) {
        server.stubFor(post(urlPathEqualTo(OPENAI_PATH))
                .withHeader("Authorization", equalTo("Bearer " + key))
                .willReturn(json(200, OPENAI_OK)));
    }

    public void openAiResponds(int status) {
        server.stubFor(post(urlPathEqualTo(OPENAI_PATH)).atPriority(10).willReturn(json(status, error(status))));
    }

    /** OpenAI가 주어진 본문(text)으로 가이드를 응답한다. delayMillis > 0이면 그만큼 늦게 응답한다. */
    public void openAiGuide(String text, String model, int delayMillis) {
        server.stubFor(post(urlPathEqualTo(OPENAI_PATH))
                .willReturn(json(
                                200,
                                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,"
                                        + "\"model\":\"" + model
                                        + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                                        + "\"content\":" + quote(text) + "},\"finish_reason\":\"stop\"}],"
                                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":10,\"total_tokens\":20}}")
                        .withFixedDelay(delayMillis)));
    }

    /** 종합 분석 형식 가이드 JSON. 이슈 출처는 sourceUrls 순서대로 쓴다. */
    public static String comprehensiveJson(String... sourceUrls) {
        StringBuilder issues = new StringBuilder();
        for (int i = 0; i < sourceUrls.length; i++) {
            if (i > 0) {
                issues.append(',');
            }
            issues.append("{\"title\":\"이슈 ")
                    .append(i + 1)
                    .append("\",\"summary\":\"이슈 요약 ")
                    .append(i + 1)
                    .append("\",\"impact\":\"NEGATIVE\",\"sourceUrl\":\"")
                    .append(sourceUrls[i])
                    .append("\",\"sourceTitle\":\"뉴스 ")
                    .append(i + 1)
                    .append("\",\"publishedDate\":\"2026-11-04\"}");
        }
        return "{\"summary\":\"최근 60일 동안 완만하게 올랐습니다.\",\"diagnosis\":\"평단가보다 0.7% 낮습니다.\","
                + "\"supplyDemand\":\"외국인이 5일 연속 순매수했습니다.\",\"issues\":[" + issues + "],"
                + "\"overall\":\"기술적 흐름과 수급은 긍정적이지만 수출 규제 이슈가 부담입니다.\",\"action\":\"WATCH\","
                + "\"rationale\":\"이슈 확인 후 판단하는 것이 좋습니다.\",\"risks\":[\"수출 규제\"]}";
    }

    /**
     * Claude가 웹 검색을 한 응답: 검색 도구 호출, 검색 결과 블록(results의 URL), 설명 문장, JSON 본문 순서다.
     */
    public void anthropicGuideWithSearch(String json, String... resultUrls) {
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < resultUrls.length; i++) {
            if (i > 0) {
                results.append(',');
            }
            results.append("{\"type\":\"web_search_result\",\"url\":\"")
                    .append(resultUrls[i])
                    .append("\",\"title\":\"검색 결과 ")
                    .append(i + 1)
                    .append("\",\"encrypted_content\":\"enc\",\"page_age\":\"2026-11-04\"}");
        }
        server.stubFor(post(urlPathEqualTo(ANTHROPIC_PATH))
                .willReturn(json(
                        200,
                        "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                                + "\"model\":\"claude-sonnet-5-5-20261001\",\"content\":["
                                + "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_1\",\"name\":\"web_search\","
                                + "\"input\":{\"query\":\"삼성전자 이슈\"}},"
                                + "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_1\",\"content\":["
                                + results + "]},"
                                + "{\"type\":\"text\",\"text\":\"검색 결과를 바탕으로 분석했습니다.\"},"
                                + "{\"type\":\"text\",\"text\":" + quote(json) + "}],"
                                + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
                                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":10,"
                                + "\"server_tool_use\":{\"web_search_requests\":1}}}")));
    }

    public static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    /** 공급자가 키를 거부하는 기본 응답(우선순위가 낮아 accepts 스텁이 먼저 맞는다). */
    public void rejectUnknownKeys() {
        anthropicResponds(401);
        openAiResponds(401);
    }

    private static String error(int status) {
        return "{\"type\":\"error\",\"error\":{\"type\":\"error_" + status + "\",\"message\":\"stub " + status + "\"}}";
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
