package com.A501.backend.domain.report.sse;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE 전송 전용 스레드 풀. 리포트 생성 스레드(AFTER_COMMIT 리스너)나
 * 하트비트 스케줄러 스레드가 느린 클라이언트 write에 막히지 않도록
 * 실제 전송을 이 풀로 분리한다.
 */
@Configuration
public class SseDeliveryConfig {

    @Bean
    public Executor sseDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("sse-delivery-");
        executor.initialize();
        return executor;
    }
}
