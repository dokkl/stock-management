package com.stockmanagement.market.application.port;

import com.stockmanagement.market.domain.IndicatorQuote;
import java.util.List;

/** 시장 지표 최신 값 저장소(market_indicator_quote). 공유 데이터라 사용자 범위가 없다. */
public interface IndicatorStore {

    /** 지표의 최신 값을 저장한다(지표당 1행, 있으면 교체). */
    void save(IndicatorQuote quote);

    List<IndicatorQuote> findAll();
}
