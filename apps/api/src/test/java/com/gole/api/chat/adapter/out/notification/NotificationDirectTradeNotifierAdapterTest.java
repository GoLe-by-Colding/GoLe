package com.gole.api.chat.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class NotificationDirectTradeNotifierAdapterTest {

    private final NotifyUseCase notifications = mock(NotifyUseCase.class);
    private final NotificationDirectTradeNotifierAdapter adapter =
            new NotificationDirectTradeNotifierAdapter(notifications);

    @Test
    void confirmationLinksRecipientToTheTradeRoom() {
        adapter.confirmationRequested("seller-1", "room-1");

        verify(notifications)
                .notify(new NotifyCommand(
                        "seller-1",
                        NotificationType.GENERAL,
                        "상대방이 직거래 완료를 확인했어요. 거래 내용을 확인해 주세요",
                        "/chat?room=room-1"));
    }

    @Test
    void notificationFailureDoesNotFailTheTradeFlow() {
        NotifyCommand command =
                new NotifyCommand("buyer-1", NotificationType.GENERAL, "양쪽 확인이 끝나 직거래가 완료됐어요", "/chat?room=room-1");
        when(notifications.notify(command)).thenThrow(new IllegalStateException("notification unavailable"));

        assertThatCode(() -> adapter.tradeCompleted("buyer-1", "room-1")).doesNotThrowAnyException();
    }

    @Test
    void notificationInsideTransactionIsDeliveredOnlyAfterCommit() {
        NotifyCommand command = new NotifyCommand(
                "seller-1", NotificationType.GENERAL, "상대방이 직거래 완료를 확인했어요. 거래 내용을 확인해 주세요", "/chat?room=room-1");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            adapter.confirmationRequested("seller-1", "room-1");

            verify(notifications, never()).notify(command);
            TransactionSynchronizationUtils.triggerAfterCommit();
            verify(notifications).notify(command);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void rolledBackTransactionDoesNotDeliverNotification() {
        NotifyCommand command = new NotifyCommand(
                "seller-1", NotificationType.GENERAL, "상대방이 직거래 완료를 확인했어요. 거래 내용을 확인해 주세요", "/chat?room=room-1");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            adapter.confirmationRequested("seller-1", "room-1");

            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(notifications, never()).notify(command);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
