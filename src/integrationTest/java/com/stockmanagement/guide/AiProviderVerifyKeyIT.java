package com.stockmanagement.guide;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import com.stockmanagement.support.AiStubs;
import com.stockmanagement.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 공급자 어댑터 키 검증(research R4): 200 VALID, 401/403 INVALID, 503·시간 초과 UNAVAILABLE. */
class AiProviderVerifyKeyIT extends IntegrationTestBase {

    private static final ApiKey KEY = ApiKey.of("sk-ant-test-key-0001");

    @Autowired
    private List<AiGuideProvider> providers;

    private final AiStubs ai = new AiStubs(WIREMOCK);

    private AiGuideProvider provider(AiProvider type) {
        return providers.stream().filter(p -> p.provider() == type).findFirst().orElseThrow();
    }

    @Test
    void anthropicAcceptsValidKeyWithMinimalRequest() {
        ai.anthropicAccepts(KEY.reveal());

        assertThat(provider(AiProvider.ANTHROPIC).verifyKey(KEY)).isEqualTo(KeyCheck.VALID);
        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.ANTHROPIC_PATH))
                .withHeader("x-api-key", equalTo(KEY.reveal())));
    }

    @Test
    void anthropicUnauthorizedOrForbiddenIsInvalid() {
        ai.anthropicResponds(401);
        assertThat(provider(AiProvider.ANTHROPIC).verifyKey(KEY)).isEqualTo(KeyCheck.INVALID);
        ai.anthropicResponds(403);
        assertThat(provider(AiProvider.ANTHROPIC).verifyKey(KEY)).isEqualTo(KeyCheck.INVALID);
    }

    @Test
    void anthropicServerErrorIsUnavailable() {
        ai.anthropicResponds(503);
        assertThat(provider(AiProvider.ANTHROPIC).verifyKey(KEY)).isEqualTo(KeyCheck.UNAVAILABLE);
    }

    @Test
    void anthropicTimeoutIsUnavailable() {
        ai.anthropicDelays(3_000);
        assertThat(provider(AiProvider.ANTHROPIC).verifyKey(KEY)).isEqualTo(KeyCheck.UNAVAILABLE);
    }

    @Test
    void openAiAcceptsValidKey() {
        ai.openAiAccepts(KEY.reveal());

        assertThat(provider(AiProvider.OPENAI).verifyKey(KEY)).isEqualTo(KeyCheck.VALID);
        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(AiStubs.OPENAI_PATH))
                .withHeader("Authorization", equalTo("Bearer " + KEY.reveal())));
    }

    @Test
    void openAiUnauthorizedIsInvalidAndServerErrorIsUnavailable() {
        ai.openAiResponds(401);
        assertThat(provider(AiProvider.OPENAI).verifyKey(KEY)).isEqualTo(KeyCheck.INVALID);
        ai.openAiResponds(500);
        assertThat(provider(AiProvider.OPENAI).verifyKey(KEY)).isEqualTo(KeyCheck.UNAVAILABLE);
    }
}
