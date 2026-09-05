package com.gole.api.notification.adapter.out.push;

import com.gole.api.notification.application.port.out.PushSenderPort;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 푸시 출력 어댑터 조립. */
@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
    public PushSenderPort fcmPushSenderPort(FcmProperties properties) {
        properties.validateEnabledConfiguration();
        log.info("FCM 푸시 발송 활성화 projectId={}", properties.projectId());
        return new FcmPushSenderAdapter(properties);
    }

    /**
     * 기본 어댑터. {@code fcm.enabled=true}가 아니면 이것이 쓰인다.
     *
     * <p>{@code @ConditionalOnMissingBean}이 아니라 반대 조건으로 명시한다 — 빈 두 개가 조건에
     * 따라 갈리는 구조에서 "없으면"은 조립 순서에 의존해 조용히 어긋날 수 있다.
     */
    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = true)
    public PushSenderPort noopPushSenderPort() {
        log.info("FCM이 구성되지 않았다 — 푸시는 발송하지 않는다(서비스는 정상 동작)");
        return new NoopPushSenderAdapter();
    }

    /**
     * 푸시 전용 실행기. 알림 발행 스레드와 분리한다.
     *
     * <p>큐를 무한으로 두지 않는다. FCM이 느려질 때 무한 큐는 메모리를 먹으며 지연만 키우고,
     * 결국 다 만료된 알림을 뒤늦게 보낸다. 가득 차면 버리는 편이 정직하다 —
     * 푸시는 유실돼도 인앱 알림이 남는다.
     */
    @Bean(destroyMethod = "shutdown")
    public Executor pushExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                4,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1_000),
                runnable -> {
                    Thread thread = new Thread(runnable, "push-dispatch");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> log.warn("푸시 대기열이 가득 차 발송을 건너뛴다"));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
