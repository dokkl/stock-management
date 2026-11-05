package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import org.springframework.stereotype.Component;

/** Claude(Anthropic) 어댑터. */
@Component
class AnthropicGuideProvider implements AiGuideProvider {

    private final AiGuideClientFactory factory;

    AnthropicGuideProvider(AiGuideClientFactory factory) {
        this.factory = factory;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public KeyCheck verifyKey(ApiKey key) {
        return KeyVerification.run(provider(), () -> factory.anthropic(key, 1));
    }
}
