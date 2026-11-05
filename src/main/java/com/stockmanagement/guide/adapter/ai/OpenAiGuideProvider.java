package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;
import org.springframework.stereotype.Component;

/** OpenAI 어댑터. */
@Component
class OpenAiGuideProvider implements AiGuideProvider {

    private final AiGuideClientFactory factory;
    private final GuideGeneration generation;

    OpenAiGuideProvider(AiGuideClientFactory factory, GuideGeneration generation) {
        this.factory = factory;
        this.generation = generation;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public KeyCheck verifyKey(ApiKey key) {
        return KeyVerification.run(provider(), () -> factory.openAi(key, 16));
    }

    @Override
    public GuideOutcome generateGuide(ApiKey key, GuidePrompt prompt) {
        return generation.generate(
                provider(),
                factory.model(provider()),
                () -> factory.openAiGuide(key, GuideGeneration.MAX_OUTPUT_TOKENS),
                prompt,
                null);
    }
}
