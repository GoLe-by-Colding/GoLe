package com.gole.api.chat.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 문의 접수 스레드와 gRPC 분석을 분리하는 작고 제한된 실행 큐다. */
@Configuration(proxyBeanMethods = false)
public class SupportAssistantAsyncConfiguration {

    public static final String EXECUTOR_BEAN_NAME = "supportAssistantTaskExecutor";

    @Bean(name = EXECUTOR_BEAN_NAME)
    ThreadPoolTaskExecutor supportAssistantTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("support-assistant-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
