package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.guide.domain.GuideContent;
import com.stockmanagement.guide.domain.GuideIssue;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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
    /**
     * 출력 토큰 상한. 한국어 장문 + 웹 검색 이슈로 4096에서 잘린 사례가 있었고, OpenAI 추론 모델은 추론 토큰도 여기에
     * 포함된다. 길이는 프롬프트의 항목별 글자 수로 줄이고, 이 값은 잘리지 않게 넉넉히 둔다.
     */
    static final int MAX_OUTPUT_TOKENS = 16_000;

    /** 출력 한도로 끝났음을 뜻하는 finish reason(Anthropic max_tokens, OpenAI length). */
    private static final Set<String> TRUNCATED_REASONS = Set.of("max_tokens", "length");

    private final String systemTemplate;

    GuideGeneration() {
        try {
            this.systemTemplate =
                    new ClassPathResource("prompts/guide-system.st").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("가이드 시스템 프롬프트를 읽을 수 없습니다", e);
        }
    }

    static final String ISSUE_RULE_SEARCH = """
            - issues: 웹 검색으로 이 종목과 관련된 최근 국내외 이슈(정치·지정학·정책·산업·기업) 중 영향이 큰 것을 최대 3개 적습니다.
              각 항목의 sourceUrl에는 실제로 검색해서 확인한 기사·자료의 URL을 그대로 적습니다. 검색 결과에 없는 URL은 쓰지 않습니다. 이슈 요약은 항목마다 100자 이내 한 문장입니다.
              impact는 종목에 대한 영향 방향(POSITIVE, NEGATIVE, NEUTRAL)입니다. 확인된 이슈가 없으면 빈 배열로 둡니다.""";
    static final String ISSUE_RULE_NONE = """
            - issues: 이번 요청에는 웹 검색을 쓸 수 없으므로 반드시 빈 배열([])로 둡니다. 기억에 의존해 이슈를 적지 않습니다.""";

    /**
     * 가이드를 생성한다.
     *
     * @param searchedUrls 웹 검색을 쓰는 공급자면 응답에서 검색 결과 URL을 꺼내는 함수, 쓰지 않으면 null. 이슈는 출처가 이
     *     목록에 있는 것만 남긴다(지어낸 출처 차단)
     */
    GuideOutcome generate(
            AiProvider provider,
            String configuredModel,
            Supplier<ChatModel> model,
            GuidePrompt prompt,
            Function<ChatResponse, Set<String>> searchedUrls) {
        BeanOutputConverter<GuideContent> converter = new BeanOutputConverter<>(GuideContent.class);
        String system = systemTemplate
                .replace("{issue_rule}", searchedUrls == null ? ISSUE_RULE_NONE : ISSUE_RULE_SEARCH)
                .replace("{format}", converter.getFormat());
        Prompt request = new Prompt(List.of(new SystemMessage(system), new UserMessage(prompt.userMessage())));
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
        String text = text(response);
        if (text == null || text.isBlank()) {
            log.warn("AI 가이드 응답이 비어 있음: provider={}", provider);
            return new GuideOutcome.TransientError(0);
        }
        return interpret(provider, modelName(response, configuredModel), text, converter, searchedUrls, response);
    }

    /** 응답 본문 해석: 구조화 성공이면 이슈 출처 검증, 실패인데 출력 한도로 끝났으면 잘림, 아니면 원문. */
    private static GuideOutcome interpret(
            AiProvider provider,
            String modelName,
            String text,
            BeanOutputConverter<GuideContent> converter,
            Function<ChatResponse, Set<String>> searchedUrls,
            ChatResponse response) {
        GuideOutcome outcome = parse(provider, modelName, text, converter);
        if (!(outcome instanceof GuideOutcome.Generated g)) {
            return outcome;
        }
        if (g.content() != null) {
            return new GuideOutcome.Generated(
                    modelName, verifyIssues(provider, g.content(), searchedUrls, response), null);
        }
        if (isTruncated(response)) {
            log.warn("AI 가이드 응답이 출력 한도에서 잘림: provider={}, length={}", provider, text.length());
            return new GuideOutcome.Truncated();
        }
        return outcome;
    }

    private static String modelName(ChatResponse response, String configuredModel) {
        return response.getMetadata() == null || isBlank(response.getMetadata().getModel())
                ? configuredModel
                : response.getMetadata().getModel();
    }

    /** 마지막 생성 결과의 finish reason이 출력 한도 도달이면 잘린 응답이다. */
    static boolean isTruncated(ChatResponse response) {
        List<Generation> results = response.getResults();
        if (results.isEmpty() || results.getLast().getMetadata() == null) {
            return false;
        }
        String reason = results.getLast().getMetadata().getFinishReason();
        return reason != null && TRUNCATED_REASONS.contains(reason.strip().toLowerCase(Locale.ROOT));
    }

    /** 웹 검색을 쓰면 응답이 여러 텍스트 블록(검색 전 설명, 인용 문단, JSON)으로 나뉘므로 모두 이어 붙인다. */
    private static String text(ChatResponse response) {
        String joined = response.getResults().stream()
                .map(Generation::getOutput)
                .filter(Objects::nonNull)
                .map(output -> output.getText())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return joined.isBlank() ? null : joined;
    }

    /** 이슈 출처가 실제 검색 결과에 없으면 버린다. 검색을 쓰지 않는 공급자는 이슈를 비운다. */
    private static GuideContent verifyIssues(
            AiProvider provider,
            GuideContent content,
            Function<ChatResponse, Set<String>> searchedUrls,
            ChatResponse response) {
        if (searchedUrls == null || content.issues() == null) {
            return content.withIssues(List.of());
        }
        Set<String> allowed = searchedUrls.apply(response).stream()
                .map(GuideIssue::normalizeUrl)
                .collect(Collectors.toSet());
        List<GuideIssue> verified = content.issues().stream()
                .filter(issue -> allowed.contains(GuideIssue.normalizeUrl(issue.sourceUrl())))
                .toList();
        if (verified.size() < content.issues().size()) {
            log.info(
                    "검색 결과에 없는 출처의 이슈를 제외: provider={}, dropped={}",
                    provider,
                    content.issues().size() - verified.size());
        }
        return content.withIssues(verified);
    }

    /** 응답 앞뒤에 설명 문장이 붙어도 첫 '{'부터 마지막 '}'까지를 JSON으로 본다. */
    static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private static GuideOutcome parse(
            AiProvider provider, String modelName, String text, BeanOutputConverter<GuideContent> converter) {
        try {
            GuideContent content = converter.convert(extractJson(text));
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
