package com.stockmanagement.guide.application;

import java.util.List;

/**
 * 종목별 가이드 기록 한 페이지(최신순, 20건).
 *
 * @param page 0부터 시작하는 페이지 번호
 */
public record GuideHistoryPage(
        long securityId,
        String securityName,
        String symbol,
        List<GuideHistoryEntry> entries,
        int page,
        boolean hasNext) {

    public static final int PAGE_SIZE = 20;

    public GuideHistoryPage {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty() && page == 0;
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
