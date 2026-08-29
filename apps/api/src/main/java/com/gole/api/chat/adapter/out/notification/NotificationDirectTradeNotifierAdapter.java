package com.gole.api.chat.adapter.out.notification;

import com.gole.api.chat.application.port.out.DirectTradeNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 직거래 상태 알림을 notification 컨텍스트에 위임하는 best-effort 어댑터. */
@Component
public class NotificationDirectTradeNotifierAdapter implements DirectTradeNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationDirectTradeNotifierAdapter.class);

    private final NotifyUseCase notifications;

    public NotificationDirectTradeNotifierAdapter(NotifyUseCase notifications) {
        this.notifications = notifications;
    }

    @Override
    public void confirmationRequested(String recipientId, String roomId) {
        send(recipientId, roomId, "상대방이 직거래 완료를 확인했어요. 거래 내용을 확인해 주세요");
    }

    @Override
    public void tradeCompleted(String recipientId, String roomId) {
        send(recipientId, roomId, "양쪽 확인이 끝나 직거래가 완료됐어요");
    }

    private void send(String recipientId, String roomId, String message) {
        Runnable delivery = () -> deliver(recipientId, roomId, message);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
            return;
        }
        delivery.run();
    }

    private void deliver(String recipientId, String roomId, String message) {
        try {
            notifications.notify(
                    new NotifyCommand(recipientId, NotificationType.GENERAL, message, "/chat?room=" + roomId));
        } catch (RuntimeException failure) {
            // 알림 저장은 거래 트랜잭션의 성공 조건이 아니다. 트랜잭션이 있으면 커밋 뒤에
            // 실행되므로 Mongo 세션이 abort되어도 이미 완료된 거래를 되돌릴 수 없다.
            log.warn("직거래 상태 알림 발송 실패 recipientId={} roomId={}: {}", recipientId, roomId, failure.getMessage());
        }
    }
}
