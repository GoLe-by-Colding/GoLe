package com.gole.api.common.aop;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.operations.OperationalSignal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** 운영상 중요한 유스케이스 성공을 개인정보 없이 구조화 이벤트로 변환한다. */
@Aspect
@Component
public class OperationalSignalAspect {

    private final OperationalEventPublisher publisher;

    public OperationalSignalAspect(OperationalEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("@annotation(signal)")
    public Object publishSuccess(ProceedingJoinPoint joinPoint, OperationalSignal signal) throws Throwable {
        long startedAt = System.nanoTime();
        Object result = joinPoint.proceed();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("처리", joinPoint.getSignature().toShortString());
        fields.put("소요 시간", ((System.nanoTime() - startedAt) / 1_000_000) + "ms");
        Object[] arguments = joinPoint.getArgs();
        for (int index : signal.includeArguments()) {
            if (index >= 0 && index < arguments.length) {
                fields.put("인수 " + index, String.valueOf(arguments[index]));
            }
        }
        if (signal.includeResult() && result != null) {
            fields.put("결과", String.valueOf(result));
        }

        publisher.publish(new OperationalEvent(
                signal.category(), signal.level(), signal.title(), signal.description(), fields, Instant.now()));
        return result;
    }
}
