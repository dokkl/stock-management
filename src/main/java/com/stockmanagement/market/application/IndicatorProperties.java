package com.stockmanagement.market.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.indicator: 시장 지표 갱신·표시 설정(003 research R4).
 *
 * @param staleAfter 국내 장중 이 시간보다 오래된 지표는 "지연"
 * @param closedInterval 국내 장 외 국내 지표 갱신 간격
 */
@ConfigurationProperties("app.indicator")
public record IndicatorProperties(Duration staleAfter, Duration closedInterval) {}
