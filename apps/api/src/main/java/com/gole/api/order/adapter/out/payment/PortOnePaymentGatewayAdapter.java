package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.application.port.out.PaymentGatewayPort;
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
            Map<?, ?> payment = client.get()
                    .uri("/payments/{paymentId}", orderId)
                    .retrieve()
                    .body(Map.class);
            if (payment == null) {
                return false;
            }
            String status = String.valueOf(payment.get("status"));
            long paidTotal = extractPaidTotal(payment);
            boolean ok = "PAID".equals(status) && paidTotal == amount;
            if (!ok) {
                log.warn("[PortOne] 검증 실패 orderId={} status={} paid={} expected={}",
                        orderId, status, paidTotal, amount);
            }
            return ok;
        } catch (Exception ex) {
            log.error("[PortOne] 결제 조회 실패 orderId={}: {}", orderId, ex.getMessage());
            return false;
        }
    }

    @Override
    public void refund(String orderId, long amount) {
        try {
            client.post()
                    .uri("/payments/{paymentId}/cancel", orderId)
                    .body(Map.of("reason", "주문 환불"))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("PortOne cancel failed: " + res.getStatusCode());
                    })
                    .toBodilessEntity();
            log.info("[PortOne] 환불 요청 완료 orderId={} amount={}", orderId, amount);
        } catch (Exception ex) {
            log.error("[PortOne] 환불 실패 orderId={}: {}", orderId, ex.getMessage());
            throw new IllegalStateException("PortOne refund failed for order " + orderId, ex);
        }
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
