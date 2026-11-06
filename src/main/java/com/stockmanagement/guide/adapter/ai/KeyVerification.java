package com.stockmanagement.guide.adapter.ai;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.KeyCheck;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

/** 최소 토큰 요청 1회로 키를 확인한다(research R4 Implementation note). */
final class KeyVerification {

    private static final Logger log = LoggerFactory.getLogger(KeyVerification.class);
    private static final String PING = "ping";

    private KeyVerification() {}

    static KeyCheck run(AiProvider provider, Supplier<ChatModel> model) {
        try {
            model.get().call(PING);
            return KeyCheck.VALID;
        } catch (RuntimeException e) {
            KeyCheck check = ProviderErrors.classify(e);
            log.info(
                    "AI 키 검증 실패: provider={}, result={}, status={}, type={}",
                    provider,
                    check,
                    ProviderErrors.statusCode(e),
                    e.getClass().getSimpleName());
            return check;
        }
    }
}
