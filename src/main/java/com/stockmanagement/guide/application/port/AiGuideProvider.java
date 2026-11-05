package com.stockmanagement.guide.application.port;

import com.stockmanagement.aicredential.domain.AiProvider;
import com.stockmanagement.aicredential.domain.ApiKey;
import com.stockmanagement.aicredential.domain.KeyCheck;

/** AI 공급자 어댑터 포트(contracts/ports.md). 가이드 생성(generateGuide)은 US3에서 추가한다. */
public interface AiGuideProvider {

    AiProvider provider();

    /** 최소 토큰 요청 1회로 키를 확인한다. 예외를 던지지 않고 결과로 돌려준다. */
    KeyCheck verifyKey(ApiKey key);
}
