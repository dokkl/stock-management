package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 공급자 공통 가이드 생성(research R4): 시스템 프롬프트 + 출력 스키마 안내로 요청하고, 응답을 GuideContent로 바꾼다.
 * 스키마에 맞지 않으면 원문을 그대로 돌려준다. 오류는 키 문제와 일시 장애로 나눈다(FR-023e).
 */
@Component
class GuideGeneration {

    private static final Logger log = LoggerFactory.getLogger(GuideGeneration.class);
    static final int MAX_OUTPUT_TOKENS = 4096;

    private final String systemTemplate;

    GuideGeneration() {
        try {
            this.systemTemplate =
                    new ClassPathResource("prompts/guide-system.st").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("가이드 시스템 프롬프트를 읽을 수 없습니다", e);
        }
    }

    GuideOutcome generate(AiProvider provider, String configuredModel, Supplier<ChatModel> model, GuidePrompt prompt) {
        BeanOutputConverter<GuideContent> converter = new BeanOutputConverter<>(GuideContent.class);
        Prompt request = new Prompt(List.of(
                new SystemMessage(systemTemplate.replace("{format}", converter.getFormat())),
                new UserMessage(prompt.userMessage())));
        ChatResponse response;
        try {
            response = model.get().call(request);
        } catch (RuntimeException e) {
            GuideOutcome outcome = classify(e);
            log.info(
                    "AI 가이드 생성 실패: provider={}, outcome={}, type={}",
                    provider,
                    outcome,
                    e.getClass().getSimpleName());
            return outcome;
        }
        String text = response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            log.warn("AI 가이드 응답이 비어 있음: provider={}", provider);
            return new GuideOutcome.TransientError(0);
        }
        String modelName =
                response.getMetadata() == null || isBlank(response.getMetadata().getModel())
                        ? configuredModel
                        : response.getMetadata().getModel();
        return parse(provider, modelName, text, converter);
    }

    private static GuideOutcome parse(
            AiProvider provider, String modelName, String text, BeanOutputConverter<GuideContent> converter) {
        try {
            GuideContent content = converter.convert(text);
            if (content != null) {
                return new GuideOutcome.Generated(modelName, content, null);
            }
        } catch (RuntimeException e) {
            log.info(
                    "AI 가이드 구조화 실패, 원문 저장: provider={}, type={}",
                    provider,
                    e.getClass().getSimpleName());
        }
        return new GuideOutcome.Generated(modelName, null, text.strip());
    }

    /**
     * 401·402·403과 잔액 부족(Anthropic 400 "credit balance", OpenAI 429 "insufficient_quota")은 키 문제, 나머지는 일시
     * 장애다. 예외 메시지는 분류에만 쓰고 로그에 남기지 않는다.
     */
    static GuideOutcome classify(Throwable error) {
        OptionalInt status = ProviderErrors.statusCode(error);
        if (status.isEmpty()) {
            return new GuideOutcome.TransientError(0);
        }
        int code = status.getAsInt();
        String message = (error.getMessage() + " " + ProviderErrors.errorBody(error)).toLowerCase(Locale.ROOT);
        boolean noBalance = (code == 400 && message.contains("credit balance"))
                || (code == 429 && message.contains("insufficient_quota"));
        if (code == 401 || code == 402 || code == 403 || noBalance) {
            return new GuideOutcome.CredentialError(code);
        }
        return new GuideOutcome.TransientError(code);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
