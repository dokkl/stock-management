package com.stockmanagement.common.config;

import com.stockmanagement.common.domain.KstDate;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 모든 시간 판단은 주입된 Clock을 쓴다(헌법 원칙 II 결정성). */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(KstDate.ZONE);
    }
}
