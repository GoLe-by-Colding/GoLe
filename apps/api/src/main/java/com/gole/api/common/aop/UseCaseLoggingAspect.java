package com.gole.api.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 횡단 관심사: 애플리케이션 서비스(유스케이스) 실행 로깅/소요시간 측정.
 * 도메인/유스케이스 코드를 깨끗하게 유지하기 위해 AOP로 분리한다.
 */
@Aspect
@Component
public class UseCaseLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(UseCaseLoggingAspect.class);

    // 각 bounded context의 application.service 패키지 내 모든 public 메서드
    @Pointcut("execution(public * com.gole.api..application.service..*(..))"
            + " && !@within(org.springframework.boot.context.properties.ConfigurationProperties)")
    void applicationServiceMethods() {}

    @Around("applicationServiceMethods()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = joinPoint.getSignature().toShortString();
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("[UseCase] {} 완료 ({}ms)", signature, elapsedMs);
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            // 유스케이스 예외 메시지에는 이메일·문의 본문·외부 사업자 응답처럼 이용자가
            // 제출한 값이 포함될 수 있다. 공통 로그에는 진단에 필요한 예외 종류만 남기고,
            // 사용자 응답/운영 알림은 GlobalExceptionHandler의 비식별 참조값으로 추적한다.
            log.warn(
                    "[UseCase] {} 실패 ({}ms): error={}",
                    signature,
                    elapsedMs,
                    ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
