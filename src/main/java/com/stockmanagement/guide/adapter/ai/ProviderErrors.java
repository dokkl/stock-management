package com.stockmanagement.guide.adapter.ai;

import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIServiceException;
import com.stockmanagement.aicredential.domain.KeyCheck;
import java.util.OptionalInt;

/**
 * 공급자 SDK 예외를 키 검증 결과로 분류한다(research R4, FR-023e). 401/403은 키 문제, 나머지(시간 초과·429·5xx)는
 * 일시 장애다. 예외 메시지는 로그에 남기지 않는다(키가 섞일 수 있음).
 */
final class ProviderErrors {

    private ProviderErrors() {}

    static KeyCheck classify(Throwable error) {
        OptionalInt status = statusCode(error);
        if (status.isPresent() && (status.getAsInt() == 401 || status.getAsInt() == 403)) {
            return KeyCheck.INVALID;
        }
        return KeyCheck.UNAVAILABLE;
    }

    /** 원인 사슬에서 공급자 오류 응답 본문을 찾는다(분류용, 로그에 남기지 않는다). 없으면 빈 문자열. */
    static String errorBody(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AnthropicServiceException e) {
                return String.valueOf(e.body());
            }
            if (t instanceof OpenAIServiceException e) {
                return String.valueOf(e.body());
            }
        }
        return "";
    }

    /** 원인 사슬에서 HTTP 상태 코드를 찾는다. 없으면(시간 초과·연결 실패) 비어 있다. */
    static OptionalInt statusCode(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AnthropicServiceException e) {
                return OptionalInt.of(e.statusCode());
            }
            if (t instanceof OpenAIServiceException e) {
                return OptionalInt.of(e.statusCode());
            }
        }
        return OptionalInt.empty();
    }
}
