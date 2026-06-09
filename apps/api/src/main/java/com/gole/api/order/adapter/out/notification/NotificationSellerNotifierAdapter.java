package com.gole.api.order.adapter.out.notification;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import com.gole.api.order.application.port.out.SellerNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 셀러 알림 아웃바운드 어댑터. notification 컨텍스트의 인바운드 포트({@link NotifyUseCase})로
 * 위임한다(NFR-3: 컨텍스트 간 인바운드 포트 의존). 알림 실패는 흡수해 주문 흐름을 막지 않는다.
 */
@Component
public class NotificationSellerNotifierAdapter implements SellerNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationSellerNotifierAdapter.class);

    private final NotifyUseCase notifyUseCase;

    public NotificationSellerNotifierAdapter(NotifyUseCase notifyUseCase) {
        this.notifyUseCase = notifyUseCase;
    }

    @Override
    public void notifyOrderPlaced(String sellerId, String orderId, long amount) {
        try {
            notifyUseCase.notify(new NotifyCommand(
                    sellerId,
                    NotificationType.ORDER_PLACED,
                    "내 매물에 주문이 들어왔어요 (₩" + String.format("%,d", amount) + ")",
                    "/orders/" + orderId));
        } catch (RuntimeException e) {
            // best-effort: 알림 실패가 주문 생성을 막지 않는다.
            log.warn("주문 알림 발송 실패 sellerId={} orderId={}: {}", sellerId, orderId, e.getMessage());
        }
    }
}
