package com.stockmanagement.aicredential.domain;

/** 지원하는 AI 공급자(FR-023, FR-024). 공급자를 추가하면 값 하나와 어댑터 빈 하나가 늘어난다. */
public enum AiProvider {
    ANTHROPIC("Claude"),
    OPENAI("OpenAI");

    private final String displayName;

    AiProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
