package com.stockmanagement.guide.domain;

import java.util.List;

/**
 * AI가 돌려준 구조화 가이드(FR-017, contracts/ai-guide-output.schema.json). 스키마 한도를 어기면 생성할 수 없다.
 * 면책 문구는 AI가 아니라 화면이 고정 문구로 붙인다.
 */
public record GuideContent(String summary, String diagnosis, GuideAction action, String rationale, List<String> risks) {

    public static final int MAX_SUMMARY = 1500;
    public static final int MAX_DIAGNOSIS = 1500;
    public static final int MAX_RATIONALE = 2000;
    public static final int MAX_RISKS = 5;
    public static final int MAX_RISK = 300;

    public GuideContent {
        requireText("summary", summary, MAX_SUMMARY);
        requireText("diagnosis", diagnosis, MAX_DIAGNOSIS);
        requireText("rationale", rationale, MAX_RATIONALE);
        if (action == null) {
            throw new IllegalArgumentException("action이 없습니다");
        }
        if (risks == null || risks.isEmpty() || risks.size() > MAX_RISKS) {
            throw new IllegalArgumentException("risks는 1~5개여야 합니다");
        }
        risks.forEach(r -> requireText("risks[]", r, MAX_RISK));
        risks = List.copyOf(risks);
    }

    private static void requireText(String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + "는 1~" + max + "자여야 합니다");
        }
    }
}
