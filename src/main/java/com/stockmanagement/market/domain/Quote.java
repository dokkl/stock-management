package com.stockmanagement.market.domain;

import com.stockmanagement.common.domain.Direction;
import com.stockmanagement.common.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 종목의 최신 시세 1건(data-model.md quote). 등락은 전일 종가 대비로 계산한다.
 *
 * @param sessionDate 이 시세가 속한 거래일. prevClose는 이 날 직전 거래일의 종가다.
 * @param asOf 제공자가 알려준 시세 기준 시각
 * @param fetchedAt 마지막으로 성공적으로 받아온 시각(신선도 판단 기준)
 */
public record Quote(
        Long securityId,
        BigDecimal price,
        BigDecimal prevClose,
        LocalDate sessionDate,
        MarketState marketState,
        Instant asOf,
        Instant fetchedAt) {

    public Quote {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(prevClose, "prevClose");
        Objects.requireNonNull(sessionDate, "sessionDate");
        Objects.requireNonNull(marketState, "marketState");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }

    public BigDecimal change() {
        return price.subtract(prevClose);
    }

    /** 전일 대비 등락률(비율). 소수 넷째 자리 HALF_UP, 전일 종가가 0이면 0. */
    public BigDecimal changeRate() {
        if (prevClose.signum() == 0) {
            return BigDecimal.ZERO.setScale(Money.RATIO_SCALE);
        }
        return change().divide(prevClose, Money.RATIO_SCALE, RoundingMode.HALF_UP);
    }

    public Direction direction() {
        return Direction.of(change());
    }

    public QuoteStatus status(Instant now, Duration staleAfter) {
        return switch (marketState) {
            case OPEN -> fetchedAt.plus(staleAfter).isBefore(now) ? QuoteStatus.STALE : QuoteStatus.LIVE;
            case PRE_OPEN -> QuoteStatus.PRE_OPEN;
            case CLOSED, HOLIDAY -> QuoteStatus.CLOSED;
        };
    }
}
