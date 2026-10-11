package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSendException.FailureType;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.AlimtalkAcceptance;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.SendAlimtalkCommand;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlimtalkDispatchServiceTest {

    private final AlimtalkSenderPort sender = mock(AlimtalkSenderPort.class);

    @Test
    @DisplayName("사업자가 접수하면 true")
    void send_acceptedIsTrue() {
        when(sender.send(any())).thenReturn(new AlimtalkAcceptance("g", "m", "2000", "정상 접수"));

        assertThat(new AlimtalkDispatchService(Optional.of(sender)).send("01012345678", "T1", Map.of("code", "123456")))
                .isTrue();
        verify(sender).send(new SendAlimtalkCommand("01012345678", "T1", Map.of("code", "123456")));
    }

    @Test
    @DisplayName("발송 빈이 없거나 접수가 거절되면 false")
    void send_unavailableOrRejectedIsFalse() {
        when(sender.send(any())).thenThrow(new AlimtalkSendException(FailureType.PROVIDER_REJECTED, "rejected"));

        assertThat(new AlimtalkDispatchService(Optional.empty()).send("01012345678", "T1", Map.of()))
                .isFalse();
        assertThat(new AlimtalkDispatchService(Optional.of(sender)).send("01012345678", "T1", Map.of()))
                .isFalse();
    }
}
