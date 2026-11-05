package com.stockmanagement.guide.adapter.ai;

import com.anthropic.models.messages.Model;
import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 요청마다 사용자 키로 공급자별 ChatModel을 만든다(research R4). Spring AI 자동 구성 빈(정적 키)은 쓰지 않는다.
 * 재시도는 하지 않는다: 키 검증과 가이드 요청은 실패를 그대로 사용자에게 알린다.
 */
@Component
public class AiGuideClientFactory {

    static final String ANTHROPIC_URL = "https://api.anthropic.com";
    static final String OPENAI_URL = "https://api.openai.com/v1";

    private final AiProperties properties;

    public AiGuideClientFactory(AiProperties properties) {
        this.properties = properties;
    }

    public ChatModel anthropic(ApiKey key, int maxTokens) {
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(key.reveal())
                .baseUrl(properties.baseUrl(AiProvider.ANTHROPIC, ANTHROPIC_URL))
                .timeout(properties.timeout())
                .maxRetries(0)
                .model(Model.of(properties.model(AiProvider.ANTHROPIC)))
                .maxTokens(maxTokens)
                .build();
        return AnthropicChatModel.builder().options(options).build();
    }

    public ChatModel openAi(ApiKey key, int maxTokens) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(key.reveal())
                .baseUrl(properties.baseUrl(AiProvider.OPENAI, OPENAI_URL))
                .timeout(properties.timeout())
                .maxRetries(0)
                .model(properties.model(AiProvider.OPENAI))
                .maxCompletionTokens(maxTokens)
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    public String model(AiProvider provider) {
        return properties.model(provider);
    }
}
