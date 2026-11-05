package com.stockmanagement.guide.domain;

import java.util.List;

/**
 * AI가 돌려준 구조화 가이드(FR-017, contracts/ai-guide-output.schema.json). 스키마 한도를 어기면 생성할 수 없다.
 * 면책 문구는 AI가 아니라 화면이 고정 문구로 붙인다.
 *
 * <p>supplyDemand·issues·overall은 종합 분석(2026-11 확장)에서 추가되었다. 이전 기록에는 없으므로 null을 허용한다.
 *
 * @param supplyDemand 거래량·투자자별 매매·공매도 등 수급 분석
 * @param issues 웹 검색으로 확인한 국내외 이슈(0~5개). 출처가 검색 결과에 없는 항목은 저장 전에 걸러진다
 * @param overall 기술적 흐름·수급·이슈·보유 상황을 묶은 종합 판단
 */
public record GuideContent(
        String summary,
        String diagnosis,
        String supplyDemand,
        List<GuideIssue> issues,
        String overall,
        GuideAction action,
        String rationale,
        List<String> risks) {

    public static final int MAX_SUMMARY = 1500;
    public static final int MAX_DIAGNOSIS = 1500;
    public static final int MAX_RATIONALE = 2000;
    public static final int MAX_RISKS = 5;
    public static final int MAX_RISK = 300;
    public static final int MAX_SUPPLY_DEMAND = 1500;
    public static final int MAX_OVERALL = 1500;
    public static final int MAX_ISSUES = 5;

    public GuideContent {
        requireText("summary", summary, MAX_SUMMARY);
        requireText("diagnosis", diagnosis, MAX_DIAGNOSIS);
        optionalText("supplyDemand", supplyDemand, MAX_SUPPLY_DEMAND);
        optionalText("overall", overall, MAX_OVERALL);
        if (issues != null && issues.size() > MAX_ISSUES) {
            throw new IllegalArgumentException("issues는 최대 5개입니다");
        }
        issues = issues == null ? null : List.copyOf(issues);
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

    /** 종합 분석 이전 형식(요약·진단·행동·근거·위험)으로 만든다. */
    public GuideContent(String summary, String diagnosis, GuideAction action, String rationale, List<String> risks) {
        this(summary, diagnosis, null, null, null, action, rationale, risks);
    }

    /** 종합 분석 형식인지: 이전 기록에는 종합 판단이 없다. */
    public boolean isComprehensive() {
        return overall != null;
    }

    /** 이슈 목록만 바꾼 사본(출처 검증 후). */
    public GuideContent withIssues(List<GuideIssue> verified) {
        return new GuideContent(summary, diagnosis, supplyDemand, verified, overall, action, rationale, risks);
    }

    static void requireText(String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + "는 1~" + max + "자여야 합니다");
        }
    }

    static void optionalText(String field, String value, int max) {
        if (value != null && (value.isBlank() || value.length() > max)) {
            throw new IllegalArgumentException(field + "는 비어 있지 않은 " + max + "자 이하여야 합니다");
        }
    }
}
