package com.stockmanagement.market.application.port;

import com.stockmanagement.market.application.HeldSecurity;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketState;
import com.stockmanagement.market.domain.Quote;
import java.util.Collection;
import java.util.List;

/** 보유 종목 시세 저장소 포트(quote 테이블). */
public interface QuoteStore {

    /** 시장에서 하나 이상의 계좌가 보유한 종목(중복 없음)과 각 종목의 저장된 시세. */
    List<HeldSecurity> findHeldSecurities(Market market);

    /** 종목의 시세 행을 넣거나 바꾼다. */
    void save(Quote quote);

    /** 시장에 속한 모든 시세 행의 장 상태를 바꾼다. 가격과 수신 시각은 그대로 둔다. */
    void updateMarketState(Market market, MarketState state);

    /** 거래정지 여부를 반영한다. 상장폐지 종목은 바꾸지 않는다. */
    void updateSuspension(Collection<Long> suspendedIds, Collection<Long> activeIds);
}
