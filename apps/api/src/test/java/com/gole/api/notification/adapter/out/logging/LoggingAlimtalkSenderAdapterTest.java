package com.gole.api.notification.adapter.out.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.SendAlimtalkCommand;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingAlimtalkSenderAdapterTest {

    private static final SendAlimtalkCommand COMMAND =
            new SendAlimtalkCommand("01012345678", "template-id", Map.of("#{인증번호}", "123456"));

    @Test
    void localDefaultRedactsTheOtpValue() {
        LoggingAlimtalkSenderAdapter adapter = new LoggingAlimtalkSenderAdapter("local", false);

        String log = captureLog(() -> adapter.send(COMMAND));

        assertThat(log).contains("0101234****", "sensitiveValues=redacted", "#{인증번호}");
        assertThat(log).doesNotContain("123456");
    }

    @Test
    void localCanExplicitlyOptInToCodeLogging() {
        LoggingAlimtalkSenderAdapter adapter = new LoggingAlimtalkSenderAdapter("e2e", true);

        assertThat(captureLog(() -> adapter.send(COMMAND))).contains("123456");
    }

    @Test
    void publicEnvironmentFailsClosedEvenWhenSensitiveLoggingWasRequested() {
        LoggingAlimtalkSenderAdapter adapter = new LoggingAlimtalkSenderAdapter("production", true);

        assertThatThrownBy(() -> adapter.send(COMMAND))
                .isInstanceOf(AlimtalkSendException.class)
                .hasMessageNotContaining("123456")
                .hasMessageNotContaining("01012345678");
    }

    private static String captureLog(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingAlimtalkSenderAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
