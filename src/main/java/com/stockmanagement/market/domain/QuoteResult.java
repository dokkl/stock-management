package com.stockmanagement.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 종목 1건의 현재가 조회 결과. 한 종목이 실패해도 나머지는 성공으로 돌려준다(contracts/ports.md). */
public sealed interface QuoteResult {

    String symbol();

    record Success(String symbol, BigDecimal price, Instant asOf) implements QuoteResult {
        public Success {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(asOf, "asOf");
        }
    }

    record Failure(String symbol, Reason reason) implements QuoteResult {
        public Failure {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        RATE_LIMITED,
        NOT_FOUND,
        UPSTREAM_ERROR
    }
}
