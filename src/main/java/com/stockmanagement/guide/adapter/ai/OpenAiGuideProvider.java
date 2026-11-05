package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import org.springframework.stereotype.Component;

/** OpenAI 어댑터. */
@Component
class OpenAiGuideProvider implements AiGuideProvider {

    private final AiGuideClientFactory factory;

    OpenAiGuideProvider(AiGuideClientFactory factory) {
        this.factory = factory;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public KeyCheck verifyKey(ApiKey key) {
        return KeyVerification.run(provider(), () -> factory.openAi(key, 16));
    }
}
