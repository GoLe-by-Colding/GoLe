package com.gole.api.parts.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NotificationPartRequestHolderNotifierAdapterTest {

    @Test
    @DisplayName("보유자 알림 문구·링크·중복 키·종류를 스펙대로 보낸다")
    void notifyOwner_sendsSpecifiedCommand() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);

        new NotificationPartRequestHolderNotifierAdapter(notifications).notifyOwner("owner-1", "pr-1", "10305");

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications).notify(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new NotifyCommand(
                        "owner-1",
                        NotificationType.PART_REQUEST_FOR_OWNED_SET,
                        "보유한 10305 세트의 부품을 찾는 요청이 있어요",
                        "/parts/pr-1",
                        "part-request:pr-1"));
        // 수신 설정은 notification이 종류의 분류로 판정한다 — 커뮤니티 소식이어야 한다(W8).
        assertThat(NotificationType.PART_REQUEST_FOR_OWNED_SET.category()).isEqualTo(NotificationCategory.COMMUNITY);
    }

    @Test
    @DisplayName("발송 실패는 삼킨다 — 요청 등록과 다른 보유자를 막지 않는다")
    void notifyOwner_absorbsFailure() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        when(notifications.notify(any())).thenThrow(new IllegalStateException("mongo down"));
        NotificationPartRequestHolderNotifierAdapter adapter =
                new NotificationPartRequestHolderNotifierAdapter(notifications);

        assertThatCode(() -> adapter.notifyOwner("owner-1", "pr-1", "10305")).doesNotThrowAnyException();
        assertThatCode(() -> adapter.notifyOwner("owner-2", "pr-1", "10305")).doesNotThrowAnyException();
        verify(notifications, Mockito.times(2)).notify(any());
    }
}
