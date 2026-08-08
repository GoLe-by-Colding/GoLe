package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 포트원(PortOne) V2 결제 게이트웨이 어댑터.
 *
 * <p>결제는 프론트의 브라우저 SDK가 수행하고, 서버는 결과를 <b>검증</b>한다(verify-on-server).
 * 우리 주문 id를 포트원 {@code paymentId}로 사용하므로 {@code authorize(orderId, amount)}에서
 * {@code GET /payments/{orderId}} 로 결제 상태(PAID)와 금액 일치를 확인한다.
 *
 * <p>활성화: {@code portone.enabled=true} + {@code portone.api-secret} 설정 필요.
 * 미설정 시 {@link StubPaymentGatewayAdapter}가 사용된다.
 */
@Component
@ConditionalOnProperty(name = "portone.enabled", havingValue = "true")
public class PortOnePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PortOnePaymentGatewayAdapter.class);

    private final RestClient client;

    public PortOnePaymentGatewayAdapter(
            @Value("${portone.api-base:https://api.portone.io}") String apiBase,
            @Value("${portone.api-secret}") String apiSecret) {
        this.client = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
    }

    @Override
    public boolean authorize(String orderId, long amount) {
        try {
            Map<?, ?> payment = fetchPayment(orderId);
            if (payment == null) {
                return false;
            }
            String status = String.valueOf(payment.get("status"));
            long paidTotal = extractPaidTotal(payment);
            boolean ok = "PAID".equals(status) && paidTotal == amount;
            if (!ok) {
                log.warn(
                        "[PortOne] 검증 실패 orderId={} status={} paid={} expected={}", orderId, status, paidTotal, amount);
            }
            return ok;
        } catch (Exception ex) {
            log.error("[PortOne] 결제 조회 실패 orderId={}: {}", orderId, ex.getMessage());
            // 조회 실패는 결제 거절이 아니다. false를 반환하면 매물 선점이 잘못 풀리므로 재시도 가능한 예외로 분리한다.
            throw new PaymentGatewayUnavailableException(orderId, ex);
        }
    }

    @Override
    public RefundResult refund(String orderId, long amount) {
        try {
            Map<?, ?> payment = fetchPayment(orderId);
            if ("CANCELLED".equals(String.valueOf(payment.get("status")))) {
                return RefundResult.SUCCEEDED;
            }

            Map<?, ?> response = client.post()
                    .uri("/payments/{paymentId}/cancel", orderId)
                    .body(Map.of(
                            "reason", "주문 환불",
                            "amount", amount,
                            "currentCancellableAmount", amount))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("PortOne cancel failed: " + res.getStatusCode());
                    })
                    .body(Map.class);
            String cancellationStatus = extractCancellationStatus(response);
            RefundResult result =
                    switch (cancellationStatus) {
                        case "SUCCEEDED" -> RefundResult.SUCCEEDED;
                        case "REQUESTED" -> RefundResult.REQUESTED;
                        default -> throw new IllegalStateException(
                                "Unexpected cancellation status: " + cancellationStatus);
                    };
            log.info("[PortOne] 환불 응답 orderId={} amount={} status={}", orderId, amount, cancellationStatus);
            return result;
        } catch (Exception ex) {
            log.error("[PortOne] 환불 실패 orderId={}: {}", orderId, ex.getMessage());
            throw new PaymentGatewayUnavailableException(orderId, ex);
        }
    }

    @Override
    public boolean isFullyRefunded(String orderId, long amount) {
        try {
            Map<?, ?> payment = fetchPayment(orderId);
            return "CANCELLED".equals(String.valueOf(payment.get("status"))) && extractPaidTotal(payment) == amount;
        } catch (Exception ex) {
            throw new PaymentGatewayUnavailableException(orderId, ex);
        }
    }

    private Map<?, ?> fetchPayment(String orderId) {
        Map<?, ?> payment =
                client.get().uri("/payments/{paymentId}", orderId).retrieve().body(Map.class);
        if (payment == null) {
            throw new IllegalStateException("PortOne payment response is empty");
        }
        return payment;
    }

    private static String extractCancellationStatus(Map<?, ?> response) {
        if (response != null && response.get("cancellation") instanceof Map<?, ?> cancellation) {
            return String.valueOf(cancellation.get("status"));
        }
        return "UNKNOWN";
    }

    private static long extractPaidTotal(Map<?, ?> payment) {
        Object amountObj = payment.get("amount");
        if (amountObj instanceof Map<?, ?> amountMap) {
            Object total = amountMap.get("total");
            if (total instanceof Number n) {
                return n.longValue();
            }
        }
        return -1;
    }
}
