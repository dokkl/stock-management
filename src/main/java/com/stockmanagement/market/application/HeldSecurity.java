package com.stockmanagement.market.application;

import com.stockmanagement.market.domain.Quote;

/**
 * 하나 이상의 계좌가 보유한 종목 1건과 저장된 시세.
 *
 * @param quote 저장된 시세. 없으면 null
 */
public record HeldSecurity(long securityId, String symbol, Quote quote) {}
