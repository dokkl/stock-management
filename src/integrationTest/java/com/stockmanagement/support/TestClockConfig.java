package com.stockmanagement.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 모든 통합 테스트가 공유하는 테스트 빈: 조작 가능한 시계(기본 2026-11-05 10:00 KST, 목요일)와 SQL 카운터. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final String DEFAULT_INSTANT = "2026-11-05T01:00:00Z";

    @Bean
    static QueryCounter queryCounter() {
        return new QueryCounter();
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return MutableClock.at(DEFAULT_INSTANT);
    }
}
