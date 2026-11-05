package com.stockmanagement.market.application.port;

import com.stockmanagement.market.domain.Exchange;
import com.stockmanagement.market.domain.Market;
import com.stockmanagement.market.domain.MarketCalendar;
import com.stockmanagement.market.domain.QuoteResult;
import com.stockmanagement.market.domain.SecurityListing;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 시세·종목 데이터 제공자 포트(contracts/ports.md). */
public interface MarketDataProvider {

    /** 거래소의 상장 종목 목록. 실패 시 예외를 던진다(부분 결과를 반환하지 않는다). */
    List<SecurityListing> fetchSecurityMaster(Exchange exchange);

    /** 종목별 현재가. 입력 순서대로 종목마다 결과 1건을 돌려주며, 실패는 예외 대신 {@link QuoteResult.Failure}다. */
    List<QuoteResult> fetchQuotes(List<String> symbols);

    /** 시장의 오늘 장 운영 정보. 실패 시 예외를 던진다. */
    MarketCalendar fetchMarketCalendar(Market market);

    /** 기준일 직전 거래일의 종가(일봉). 해당 봉이 없으면 비어 있다. 호출 실패 시 예외를 던진다. */
    Optional<BigDecimal> fetchPreviousClose(String symbol, LocalDate referenceDate);

    /** 주어진 종목 중 거래정지된 종목. 제공자가 정지 여부를 알려주지 않는 종목은 포함하지 않는다. */
    Set<String> fetchSuspendedSymbols(List<String> symbols);
}
