package com.stockmanagement.guide.application.port;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;
import com.stockmanagement.guide.domain.GuideOutcome;
import com.stockmanagement.guide.domain.GuidePrompt;

/** AI 공급자 어댑터 포트(contracts/ports.md). 공급자를 추가하면 구현 빈이 하나 늘어난다(FR-024). */
public interface AiGuideProvider {

    AiProvider provider();

    /** 최소 토큰 요청 1회로 키를 확인한다. 예외를 던지지 않고 결과로 돌려준다. */
    KeyCheck verifyKey(ApiKey key);

    /** 가이드를 생성한다. 타임아웃은 app.ai.timeout(60초)이며, 실패도 예외 없이 결과로 돌려준다. */
    GuideOutcome generateGuide(ApiKey key, GuidePrompt prompt);
}
