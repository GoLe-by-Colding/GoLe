package com.gole.api.common.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class UseCaseLoggingAspectTest {

    @Test
    void failureLogContainsExceptionTypeButNotSensitiveMessage() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AccountService.register(..)");
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("member@example.com secret-content"));

        Logger logger = (Logger) LoggerFactory.getLogger(UseCaseLoggingAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            UseCaseLoggingAspect aspect = new UseCaseLoggingAspect();
            assertThatThrownBy(() -> aspect.logExecution(joinPoint))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("secret-content");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("error=IllegalArgumentException");
            assertThat(event.getFormattedMessage())
                    .doesNotContain("member@example.com")
                    .doesNotContain("secret-content");
        });
    }
}
