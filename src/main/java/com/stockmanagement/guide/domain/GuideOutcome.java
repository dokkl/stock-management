package com.stockmanagement.guide.domain;

import java.util.Objects;

/** AI 공급자 가이드 생성 결과(contracts/ports.md). 실패도 예외가 아니라 결과로 돌려준다. */
public sealed interface GuideOutcome {

    /**
     * 생성 성공. 스키마에 맞으면 content, 맞지 않으면 rawText(원문)가 있다.
     *
     * @param model 응답한 모델명(FR-022)
     */
    record Generated(String model, GuideContent content, String rawText) implements GuideOutcome {
        public Generated {
            Objects.requireNonNull(model, "model");
            if (content == null && (rawText == null || rawText.isBlank())) {
                throw new IllegalArgumentException("가이드 결과가 비어 있습니다");
            }
        }
    }

    /** 인증 실패·잔액 부족: 사용자가 키를 확인해야 한다. */
    record CredentialError(int status) implements GuideOutcome {}

    /** 시간 초과·429·5xx 등 일시 장애. status는 HTTP 응답이 없으면 0이다. */
    record TransientError(int status) implements GuideOutcome {}
}
