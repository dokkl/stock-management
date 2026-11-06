package com.stockmanagement.market.application;

import com.stockmanagement.market.domain.IndicatorQuote;
import com.stockmanagement.market.domain.IndicatorStatus;
import com.stockmanagement.market.domain.MarketIndicator;
import java.util.List;

/** 대시보드 지표 영역: 8개 지표를 화면 순서로. */
public record IndicatorBoard(List<Card> cards) {

    public boolean isEmpty() {
        return cards.stream().noneMatch(Card::hasQuote);
    }

    public boolean hasGlobal() {
        return cards.stream().anyMatch(c -> c.hasQuote() && !c.indicator().isRealtime());
    }

    /**
     * 지표 카드 1개.
     *
     * @param quote 최신 값. 없으면 null("정보 없음")
     */
    public record Card(MarketIndicator indicator, IndicatorQuote quote, IndicatorStatus status) {

        public boolean hasQuote() {
            return quote != null;
        }
    }
}
