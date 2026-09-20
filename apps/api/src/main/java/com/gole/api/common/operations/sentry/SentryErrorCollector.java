package com.gole.api.common.operations.sentry;

import io.sentry.SentryClient;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import jakarta.annotation.PreDestroy;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 기존 API 운영 오류 경계의 수집 전용 어댑터. 큐 수락은 외부 전달 성공을 뜻하지 않는다. */
@Component
public class SentryErrorCollector {
    private final SentryClient client;
    private final LongSupplier clock;
    private long lastAttempt = Long.MIN_VALUE;

    @Autowired
    public SentryErrorCollector(
            @Value("${gole.sentry.enabled:false}") boolean enabled,
            @Value("${gole.sentry.environment:local}") String environment,
            @Value("${gole.sentry.dsn:}") String dsn) {
        this(
                enabled && "production".equals(environment) && !dsn.isBlank() ? createClient(dsn) : null,
                System::nanoTime);
    }

    SentryErrorCollector(SentryClient client, LongSupplier clock) {
        this.client = client;
        this.clock = clock;
    }

    static SentryOptions options(String dsn) {
        SentryOptions options = new SentryOptions();
        options.setDsn(dsn);
        options.setEnvironment("production");
        options.setSendDefaultPii(false);
        options.setSendClientReports(false);
        options.setAttachStacktrace(false);
        options.setAttachThreads(false);
        options.setAttachServerName(false);
        options.setEnableAutoSessionTracking(false);
        options.setEnableExternalConfiguration(false);
        options.setTracesSampleRate(0.0);
        options.getIntegrations().clear();
        options.getEventProcessors().clear();
        options.setBeforeSend((event, hint) -> {
            hint.clearAttachments();
            return SentrySafeEvents.project(event);
        });
        return options;
    }

    private static SentryClient createClient(String dsn) {
        // 잘못된 DSN은 알림/애플리케이션 기동을 막지 않으며 원문 예외를 로그에 남기지 않는다.
        try {
            return new SentryClient(options(dsn));
        } catch (RuntimeException failure) {
            return null;
        }
    }

    public synchronized void capture() {
        if (client == null) return;
        long now = clock.getAsLong();
        if (lastAttempt != Long.MIN_VALUE && now - lastAttempt < 300_000_000_000L) return;
        try {
            SentryEvent event = new SentryEvent();
            event.setLevel(SentryLevel.ERROR);
            client.captureEvent(event);
            // 전송 성공이 아니라 로컬 시도 예산이다. 실패 재수집도 최대 5분 뒤 허용한다.
            lastAttempt = now;
        } catch (RuntimeException ignored) {
            // Sentry 장애가 기존 Discord 발행을 중단시키지 않는다.
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }
}
