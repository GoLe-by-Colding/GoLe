package com.gole.api.order.adapter.out.notification;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 알림 어댑터. notification 인바운드 포트로 위임하며 실패를 흡수한다(NFR-3).
 */
@Component
public class NotificationOrderEventNotifierAdapter implements OrderEventNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrderEventNotifierAdapter.class);

    private final NotifyUseCase notifyUseCase;

    public NotificationOrderEventNotifierAdapter(NotifyUseCase notifyUseCase) {
        this.notifyUseCase = notifyUseCase;
    }

    @Override
    public void disputeOpened(String sellerId, String orderId, String reasonLabel) {
        send(sellerId, "구매자가 분쟁을 제기했어요 (" + reasonLabel + "). 내용을 확인해 주세요", orderId);
    }

    @Override
    public void disputeResolved(String buyerId, String sellerId, String orderId, boolean refunded) {
        if (refunded) {
            send(buyerId, "분쟁이 환불로 판정됐어요. 결제 금액이 환불됩니다", orderId);
            send(sellerId, "분쟁이 환불로 판정됐어요. 자세한 내용은 주문을 확인해 주세요", orderId);
        } else {
            send(buyerId, "분쟁 검토 결과 거래가 완료 처리됐어요", orderId);
            send(sellerId, "분쟁 검토 결과 거래가 완료 처리됐어요. 정산이 진행됩니다", orderId);
        }
    }

    @Override
    public void autoRefundedForNoShipment(String buyerId, String sellerId, String orderId) {
        send(buyerId, "판매자가 기한 내 발송하지 않아 자동 환불됐어요", orderId);
        send(sellerId, "기한 내 운송장이 등록되지 않아 주문이 자동 환불됐어요", orderId);
    }

    @Override
    public void shipmentReminder(String sellerId, String orderId) {
        send(sellerId, "발송 대기 중인 주문이 있어요. 운송장을 등록해 주세요 (7일 미등록 시 자동 환불)", orderId);
    }

    @Override
    public void autoCompleted(String buyerId, String sellerId, String orderId) {
        send(buyerId, "배송 완료 후 7일이 지나 구매가 자동 확정됐어요", orderId);
        send(sellerId, "구매가 자동 확정됐어요. 정산이 진행됩니다", orderId);
    }

    private void send(String recipientId, String message, String orderId) {
        try {
            notifyUseCase.notify(
                    new NotifyCommand(recipientId, NotificationType.GENERAL, message, "/orders/" + orderId));
        } catch (RuntimeException e) {
            log.warn("주문 이벤트 알림 실패 recipientId={} orderId={}: {}", recipientId, orderId, e.getMessage());
        }
    }
}
