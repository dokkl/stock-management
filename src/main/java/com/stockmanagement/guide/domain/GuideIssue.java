package com.stockmanagement.guide.domain;

import java.net.URI;
import java.util.Locale;

/**
 * AI가 웹 검색으로 확인한 국내외 이슈 1건(정치·지정학·산업·기업). 출처 URL은 http(s)여야 한다.
 *
 * @param publishedDate 기사·자료 날짜(AI가 적은 문자열, 예: 2026-11-04). 모르면 null
 */
public record GuideIssue(
        String title, String summary, IssueImpact impact, String sourceUrl, String sourceTitle, String publishedDate) {

    public static final int MAX_TITLE = 120;
    public static final int MAX_SUMMARY = 500;
    public static final int MAX_URL = 500;
    public static final int MAX_SOURCE_TITLE = 200;
    public static final int MAX_DATE = 20;

    public GuideIssue {
        GuideContent.requireText("issues[].title", title, MAX_TITLE);
        GuideContent.requireText("issues[].summary", summary, MAX_SUMMARY);
        if (impact == null) {
            throw new IllegalArgumentException("issues[].impact가 없습니다");
        }
        if (!isHttpUrl(sourceUrl)) {
            throw new IllegalArgumentException("issues[].sourceUrl은 http(s) URL이어야 합니다");
        }
        GuideContent.optionalText("issues[].sourceTitle", sourceTitle, MAX_SOURCE_TITLE);
        GuideContent.optionalText("issues[].publishedDate", publishedDate, MAX_DATE);
    }

    /** 검색 결과 URL과 비교할 때 쓰는 정규화 값: 앞뒤 공백·끝 슬래시 제거, 스킴·호스트 소문자. */
    public static String normalizeUrl(String url) {
        String trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        int pathStart = trimmed.indexOf('/', trimmed.indexOf("://") + 3);
        return pathStart < 0
                ? trimmed.toLowerCase(Locale.ROOT)
                : trimmed.substring(0, pathStart).toLowerCase(Locale.ROOT) + trimmed.substring(pathStart);
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_URL) {
            return false;
        }
        try {
            URI uri = URI.create(value.strip());
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
