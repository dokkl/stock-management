package com.stockmanagement.guide.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideIssueTest {

    private static GuideIssue issue(String url) {
        return new GuideIssue("반도체 수출 규제", "미국이 대중 수출 규제를 강화했습니다.", IssueImpact.NEGATIVE, url, "연합뉴스", "2026-11-04");
    }

    @Test
    void requiresHttpSourceUrl() {
        assertThat(issue("https://news.example.com/a").sourceUrl()).isEqualTo("https://news.example.com/a");
        assertThatThrownBy(() -> issue("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue("ftp://example.com/a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue("not a url")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issue("https://example.com/" + "a".repeat(500)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesTextFields() {
        assertThatThrownBy(() -> new GuideIssue("", "요약", IssueImpact.NEUTRAL, "https://a.com", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideIssue("제목", "요약", null, "https://a.com", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideIssue("제목", "요약", IssueImpact.NEUTRAL, "https://a.com", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new GuideIssue("제목", "요약", IssueImpact.POSITIVE, "http://a.com", null, null).sourceTitle())
                .isNull();
    }

    @Test
    void normalizesUrlForComparison() {
        assertThat(GuideIssue.normalizeUrl(" HTTPS://News.Example.com/Path/ "))
                .isEqualTo("https://news.example.com/Path");
        assertThat(GuideIssue.normalizeUrl("https://Example.com/")).isEqualTo("https://example.com");
    }

    @Test
    void comprehensiveContentKeepsIssuesAndOverall() {
        GuideContent content = new GuideContent(
                "요약", "진단", "외국인 순매수", List.of(issue("https://a.com/1")), "종합", GuideAction.WATCH, "근거", List.of("위험"));

        assertThat(content.isComprehensive()).isTrue();
        assertThat(content.withIssues(List.of()).issues()).isEmpty();
        assertThat(new GuideContent("요약", "진단", GuideAction.HOLD, "근거", List.of("위험")).isComprehensive())
                .isFalse();
        assertThatThrownBy(() -> new GuideContent(
                        "요약",
                        "진단",
                        "수급",
                        Collections.nCopies(6, issue("https://a.com/1")),
                        "종합",
                        GuideAction.HOLD,
                        "근거",
                        List.of("위험")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new GuideContent("요약", "진단", " ", List.of(), "종합", GuideAction.HOLD, "근거", List.of("위험")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
