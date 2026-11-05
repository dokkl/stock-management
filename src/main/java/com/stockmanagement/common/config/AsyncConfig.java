package com.stockmanagement.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
class AsyncConfig {

    /** AI 가이드 실행용 가상 스레드 실행기(research R8). */
    @Bean
    TaskExecutor guideExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("guide-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /** 테스트에서는 app.scheduling.enabled=false로 스케줄러를 끈다. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {}
}
