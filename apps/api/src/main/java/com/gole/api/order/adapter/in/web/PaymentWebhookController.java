package com.gole.api.order.adapter.in.web;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.ConfirmRefundUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트원(PortOne) V2 결제 웹훅. 결제 상태 변경을 서버-투-서버로 수신해 주문에 반영한다.
 *
 * <p>브라우저가 결제 후 {@code payOrder}를 호출하지 못하는 경우(탭 종료 등)에도 결제가
 * 누락되지 않도록 한다. 우리 주문 id를 {@code paymentId}로 쓰므로 webhook의 paymentId로
 * 결제 이벤트는 {@link PayOrderUseCase#pay}, 최종 취소 이벤트는
 * {@link ConfirmRefundUseCase#confirmRefund}를 호출한다. 두 경로 모두 PortOne 원장을
 * <b>재검증</b>하므로 위조 webhook만으로 결제나 환불이 확정되지 않는다.
 * 이미 처리됐거나 결제 대기가 아니면 무시하고 200으로 ack한다(PortOne 재시도 방지).
 */
@Tag(name = "Webhook", description = "결제 웹훅(포트원)")
@RestController
@RequestMapping("/api/v1/payments/portone")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PayOrderUseCase payOrderUseCase;
    private final ConfirmRefundUseCase confirmRefundUseCase;
    private final OperationalEventPublisher operationalEventPublisher;

    public PaymentWebhookController(
            PayOrderUseCase payOrderUseCase,
            ConfirmRefundUseCase confirmRefundUseCase,
            OperationalEventPublisher operationalEventPublisher) {
        this.payOrderUseCase = payOrderUseCase;
        this.confirmRefundUseCase = confirmRefundUseCase;
        this.operationalEventPublisher = operationalEventPublisher;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(@RequestBody(required = false) Map<String, Object> payload) {
        String paymentId = extractPaymentId(payload);
        if (paymentId == null || paymentId.isBlank() || paymentId.length() > 100) {
            log.warn("[PortOne webhook] paymentId 없음 payloadKeys={}", payload == null ? "[]" : payload.keySet());
            operationalEventPublisher.publish(new OperationalEvent(
                    Category.PAYMENT,
                    Level.WARNING,
                    "PortOne 웹훅 형식 오류",
                    "paymentId가 없는 결제 웹훅을 수신했습니다.",
                    Map.of(),
                    Instant.now()));
            return; // ack
        }
        String eventType = extractEventType(payload);
        try {
            if ("Transaction.Cancelled".equals(eventType)) {
                confirmRefundUseCase.confirmRefund(paymentId);
                log.info("[PortOne webhook] 전액 환불 확정 orderId={}", paymentId);
            } else if ("Transaction.PartialCancelled".equals(eventType)
                    || "Transaction.CancelPending".equals(eventType)) {
                log.info("[PortOne webhook] 환불 진행 이벤트 ack orderId={} type={}", paymentId, eventType);
            } else if ("Transaction.Paid".equals(eventType) || "Transaction.Failed".equals(eventType)) {
                payOrderUseCase.pay(paymentId); // PortOne에 재검증 후 funds-held 전이
                log.info("[PortOne webhook] 결제 반영 완료 orderId={} type={}", paymentId, eventType);
            } else {
                log.info("[PortOne webhook] 지원하지 않는 이벤트 ack orderId={} type={}", paymentId, eventType);
            }
        } catch (PaymentGatewayUnavailableException ex) {
            // 503으로 응답해야 PortOne이 웹훅을 재시도한다. 주문/매물 상태는 pay()에서 변경되기 전이다.
            log.warn("[PortOne webhook] 결제 검증 일시 장애 orderId={}", paymentId);
            throw ex;
        } catch (RuntimeException ex) {
            // 이미 처리됨/결제대기 아님/주문 없음/검증 실패 등 → ack(재시도 방지). 상세는 로깅.
            log.info("[PortOne webhook] 무시 orderId={} reason={}", paymentId, ex.getMessage());
            operationalEventPublisher.publish(new OperationalEvent(
                    Category.PAYMENT,
                    Level.WARNING,
                    "PortOne 웹훅 처리 보류",
                    "결제 웹훅이 승인 상태로 반영되지 않았습니다. 서버 로그를 확인하세요.",
                    Map.of("주문 ID", paymentId, "예외 종류", ex.getClass().getSimpleName()),
                    Instant.now()));
        }
    }

    /** PortOne V2 webhook 페이로드에서 paymentId를 추출한다(data.paymentId 우선, 상위 paymentId 폴백). */
    @SuppressWarnings("unchecked")
    private static String extractPaymentId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object pid = ((Map<String, Object>) dataMap).get("paymentId");
            if (pid != null) {
                return String.valueOf(pid);
            }
        }
        Object top = payload.get("paymentId");
        return top == null ? null : String.valueOf(top);
    }

    private static String extractEventType(Map<String, Object> payload) {
        if (payload == null || payload.get("type") == null) {
            return "";
        }
        return String.valueOf(payload.get("type"));
    }
}
