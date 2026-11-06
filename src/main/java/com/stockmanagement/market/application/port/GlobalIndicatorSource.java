package com.stockmanagement.market.application.port;

import com.stockmanagement.market.application.DailyObservation;
import com.stockmanagement.market.domain.MarketIndicator;
import java.util.Optional;

/** 미국 지수·WTI·VIX 일별 종가 제공처. */
public interface GlobalIndicatorSource {

    /** 값이 있는 최신 관측치와 그 직전 관측치. 제공처가 꺼져 있거나 값이 없으면 비어 있다. */
    Optional<DailyObservation> latest(MarketIndicator indicator);
}
