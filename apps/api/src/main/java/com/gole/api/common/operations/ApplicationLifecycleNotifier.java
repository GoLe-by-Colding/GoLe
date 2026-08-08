package com.gole.api.common.operations;

import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 새 배포가 실제로 기동 준비를 마쳤음을 Discord에 남긴다. */
@Component
public class ApplicationLifecycleNotifier {

    private final OperationalEventPublisher publisher;
    private final Environment environment;

    public ApplicationLifecycleNotifier(OperationalEventPublisher publisher, Environment environment) {
        this.publisher = publisher;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        publisher.publish(new OperationalEvent(
                Category.APPLICATION,
                Level.SUCCESS,
                "GoLe API 기동 완료",
                "애플리케이션이 요청을 받을 준비를 마쳤습니다.",
                Map.of("활성 프로필", String.join(",", environment.getActiveProfiles())),
                Instant.now()));
    }
}
