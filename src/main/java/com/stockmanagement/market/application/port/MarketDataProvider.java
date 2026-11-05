package com.stockmanagement.market.application.port;

import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.SecurityListing;
import java.util.List;

/** 시세·종목 데이터 제공자 포트(contracts/ports.md). US2에서 시세·일봉 메서드가 추가된다. */
public interface MarketDataProvider {

    /** 거래소의 상장 종목 목록. 실패 시 예외를 던진다(부분 결과를 반환하지 않는다). */
    List<SecurityListing> fetchSecurityMaster(Exchange exchange);
}
