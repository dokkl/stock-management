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
 * 시장 지표의 최신 값 1건(003 data-model). 등락은 previousClose(전일 종가·전일 값) 대비로 계산한다.
 *
 * @param previousClose 비교 기준 값. 없으면 등락 없이 값만 보여 준다
 * @param asOf 제공처가 알려 준 기준 시각
 * @param asOfDate 값이 속한 날짜(국내: 거래일, 미국: 관측일)
 * @param fetchedAt 마지막으로 받아온 시각(지연 판단)
 */
public record IndicatorQuote(
        MarketIndicator indicator,
        BigDecimal value,
        BigDecimal previousClose,
        Instant asOf,
        LocalDate asOfDate,
        Instant fetchedAt) {

    public IndicatorQuote {
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("지표 값은 양수입니다: " + indicator);
        }
    }

    public boolean hasChange() {
        return previousClose != null;
    }

    public BigDecimal change() {
        return hasChange() ? value.subtract(previousClose) : BigDecimal.ZERO;
    }

    /** 등락률(비율). 소수 넷째 자리 HALF_UP. 비교 값이 없거나 0이면 0. */
    public BigDecimal changeRate() {
        if (!hasChange() || previousClose.signum() == 0) {
            return BigDecimal.ZERO.setScale(Money.RATIO_SCALE);
        }
        return change().divide(previousClose, Money.RATIO_SCALE, RoundingMode.HALF_UP);
    }

    public Direction direction() {
        return Direction.of(change());
    }

    /**
     * 표시 상태. 미국 지표는 항상 일별 종가, 국내 지표는 국내 장 상태와 수신 시각으로 정한다.
     *
     * @param domesticState 국내 장 상태. 모르면 null(장 외로 본다)
     */
    public IndicatorStatus status(MarketState domesticState, Instant now, Duration staleAfter) {
        if (!indicator.isRealtime()) {
            return IndicatorStatus.DAILY_CLOSE;
        }
        if (domesticState != MarketState.OPEN) {
            return IndicatorStatus.CLOSED;
        }
        return fetchedAt.plus(staleAfter).isBefore(now) ? IndicatorStatus.STALE : IndicatorStatus.LIVE;
    }
}
