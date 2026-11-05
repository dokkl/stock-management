package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.guide.application.port.AiGuideProvider;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.anthropic.AnthropicWebSearchResult;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** Claude(Anthropic) 어댑터. 가이드 생성 시 웹 검색으로 국내외 이슈를 찾는다. */
@Component
class AnthropicGuideProvider implements AiGuideProvider {

    private final AiGuideClientFactory factory;
    private final GuideGeneration generation;

    AnthropicGuideProvider(AiGuideClientFactory factory, GuideGeneration generation) {
        this.factory = factory;
        this.generation = generation;
    }

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public KeyCheck verifyKey(ApiKey key) {
        return KeyVerification.run(provider(), () -> factory.anthropic(key, 1));
    }

    @Override
    public GuideOutcome generateGuide(ApiKey key, GuidePrompt prompt) {
        return generation.generate(
                provider(),
                factory.model(provider()),
                () -> factory.anthropicGuide(key, GuideGeneration.MAX_OUTPUT_TOKENS),
                prompt,
                AnthropicGuideProvider::searchedUrls);
    }

    /** Spring AI가 응답 메타데이터 "web-search-results"에 담는 검색 결과의 URL. */
    static Set<String> searchedUrls(ChatResponse response) {
        Object results =
                response.getMetadata() == null ? null : response.getMetadata().get("web-search-results");
        if (!(results instanceof List<?> list)) {
            return Set.of();
        }
        return list.stream()
                .filter(AnthropicWebSearchResult.class::isInstance)
                .map(r -> ((AnthropicWebSearchResult) r).url())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
