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
