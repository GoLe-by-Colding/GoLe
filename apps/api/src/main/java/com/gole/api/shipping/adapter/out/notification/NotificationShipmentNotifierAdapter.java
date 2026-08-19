package com.gole.api.shipping.adapter.out.notification;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import com.gole.api.shipping.application.port.out.ShipmentNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 배송 알림 아웃바운드 어댑터. notification 컨텍스트 인바운드 포트로 위임한다(NFR-3).
 * 알림 실패는 흡수한다 — 알림이 배송 상태 반영을 막으면 안 된다.
 */
@Component
public class NotificationShipmentNotifierAdapter implements ShipmentNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationShipmentNotifierAdapter.class);

    private final NotifyUseCase notifyUseCase;

    public NotificationShipmentNotifierAdapter(NotifyUseCase notifyUseCase) {
        this.notifyUseCase = notifyUseCase;
    }

    @Override
    public void notifyWaybillRegistered(String buyerId, String orderId, String carrierLabel, String waybillNumber) {
        send(buyerId, "판매자가 상품을 발송했어요 (" + carrierLabel + " " + waybillNumber + ")", orderId);
    }

    @Override
    public void notifyDelivered(String buyerId, String sellerId, String orderId) {
        send(buyerId, "상품이 배송 완료됐어요. 확인 후 구매확정을 눌러 주세요", orderId);
        send(sellerId, "판매한 상품이 배송 완료됐어요. 구매확정 후 정산됩니다", orderId);
    }

    private void send(String recipientId, String message, String orderId) {
        try {
            notifyUseCase.notify(
                    new NotifyCommand(recipientId, NotificationType.GENERAL, message, "/orders/" + orderId));
        } catch (RuntimeException e) {
            log.warn("배송 알림 발송 실패 recipientId={} orderId={}: {}", recipientId, orderId, e.getMessage());
        }
    }
}
