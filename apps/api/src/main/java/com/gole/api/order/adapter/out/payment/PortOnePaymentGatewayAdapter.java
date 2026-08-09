package com.gole.api.order.adapter.out.payment;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import com.gole.api.order.application.port.out.PaymentReviewRequiredException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 포트원(PortOne) V2 결제 게이트웨이 어댑터.
 *
 * <p>결제는 프론트의 브라우저 SDK가 수행하고, 서버는 결과를 <b>검증</b>한다(verify-on-server).
 * 우리 주문 id를 포트원 {@code paymentId}로 사용하므로 {@code verifyPayment(orderId, amount)}에서
 * {@code GET /payments/{orderId}} 로 결제 상태(PAID)와 금액 일치를 확인한다.
 *
 * <p>활성화: {@code portone.enabled=true} + API secret·상점·채널 설정 필요.
 * 미설정 시 {@link StubPaymentGatewayAdapter}가 사용된다.
 */
@Component
@ConditionalOnProperty(name = "portone.enabled", havingValue = "true")
public class PortOnePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PortOnePaymentGatewayAdapter.class);

    private final RestClient client;
    private final OperationalEventPublisher operationalEvents;
    private final String expectedStoreId;
    private final String expectedChannelKey;
    private final String expectedChannelType;

    public PortOnePaymentGatewayAdapter(
            @Value("${portone.api-base:https://api.portone.io}") String apiBase,
            @Value("${portone.api-secret}") String apiSecret,
            @Value("${portone.store-id}") String expectedStoreId,
            @Value("${portone.channel-key}") String expectedChannelKey,
            @Value("${portone.channel-type:TEST}") String expectedChannelType,
            OperationalEventPublisher operationalEvents) {
        this.client = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
        this.expectedStoreId = expectedStoreId.trim();
        this.expectedChannelKey = expectedChannelKey.trim();
        this.expectedChannelType = expectedChannelType.trim().toUpperCase(Locale.ROOT);
        this.operationalEvents = operationalEvents;
    }

    @Override
    public void preparePayment(String orderId, long amount) {
        try {
            client.post()
                    .uri("/payments/{paymentId}/pre-register", orderId)
                    .body(Map.of(
                            "storeId", expectedStoreId,
                            "totalAmount", amount,
                            "currency", "KRW"))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("PortOne pre-register failed: " + res.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("[PortOne] 결제 사전 등록 실패 orderId={}: {}", orderId, ex.getMessage());
            throw new PaymentGatewayUnavailableException(orderId, ex);
        }
    }

    @Override
    public PaymentVerificationResult verifyPayment(String orderId, long amount) {
        try {
            Map<?, ?> payment = fetchPayment(orderId);
            String status = normalizedText(payment.get("status"));
            if (status == null) {
                log.warn("[PortOne] 결제 상태 누락 orderId={}", orderId);
                publishReviewRequired(orderId, "PG 상태 누락", null);
                return PaymentVerificationResult.REVIEW_REQUIRED;
            }
            String provenanceFailure = findPaymentProvenanceFailure(payment, orderId, amount);
            if (provenanceFailure != null) {
                log.error("[PortOne] 결제 원장 기본 검증 실패 orderId={} reason={}", orderId, provenanceFailure);
                publishReviewRequired(orderId, provenanceFailure, status);
                return PaymentVerificationResult.REVIEW_REQUIRED;
            }
            if ("PAID".equals(status)) {
                String validationFailure = findPaymentValidationFailure(payment, orderId, amount);
                if (validationFailure == null) {
                    return PaymentVerificationResult.PAID;
                }
                log.error("[PortOne] 결제 원장 검증 실패 orderId={} reason={}", orderId, validationFailure);
                publishReviewRequired(orderId, validationFailure, status);
                return PaymentVerificationResult.REVIEW_REQUIRED;
            }
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                // 실패 결제는 method/channel이 없을 수 있지만, 예약을 해제하는 최종 전이 전에
                // 위에서 최소 provenance(ID·상점·V2·통화·금액)를 검증한 뒤에만 실패로 확정한다.
                return PaymentVerificationResult.FAILED;
            }
            return switch (status) {
                    // 결제 단건 조회 응답의 discriminator는 PAY_PENDING이다. PENDING은
                    // PaymentStatus 필터 값과 이전 응답에 대한 안전한 호환으로만 허용한다.
                case "READY", "PAY_PENDING", "PENDING" -> PaymentVerificationResult.PENDING;
                default -> {
                    log.warn("[PortOne] 알 수 없는 결제 상태 orderId={} status={}", orderId, status);
                    publishReviewRequired(orderId, "알 수 없는 PG 상태", status);
                    yield PaymentVerificationResult.REVIEW_REQUIRED;
                }
            };
        } catch (HttpClientErrorException.NotFound ex) {
            // 결제창을 열기 전 주문 상세를 조회한 경우 PortOne에는 paymentId가 아직 없다.
            // 즉시 실패로 확정하면 정상적인 결제 재시도를 막으므로 별도 상태로 돌려준다.
            log.info("[PortOne] 결제 건 없음 orderId={}", orderId);
            return PaymentVerificationResult.NOT_FOUND;
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
            String status = normalizedText(payment.get("status"));
            String validationFailure = findPaymentValidationFailure(payment, orderId, amount);
            if (validationFailure != null) {
                publishReviewRequired(orderId, "환불 전 " + validationFailure, status);
                throw new PaymentReviewRequiredException();
            }
            if ("CANCELLED".equals(status)) {
                if (extractCancelledTotal(payment) != amount) {
                    publishReviewRequired(orderId, "기존 전액 환불 금액 불일치 또는 누락", status);
                    throw new PaymentReviewRequiredException();
                }
                return RefundResult.SUCCEEDED;
            }
            if (!"PAID".equals(status)) {
                publishReviewRequired(orderId, "자동 환불할 수 없는 PG 상태", status);
                throw new PaymentReviewRequiredException();
            }

            Map<?, ?> response = client.post()
                    .uri("/payments/{paymentId}/cancel", orderId)
                    .body(Map.of(
                            "storeId", expectedStoreId,
                            "reason", "주문 환불",
                            "requester", "CUSTOMER",
                            "amount", amount,
                            "currentCancellableAmount", amount))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("PortOne cancel failed: " + res.getStatusCode());
                    })
                    .body(Map.class);
            String cancellationStatus = extractCancellationStatus(response);
            long cancellationTotal = extractCancellationTotal(response);
            if (cancellationTotal != amount) {
                publishReviewRequired(orderId, "환불 응답 금액 불일치 또는 누락", cancellationStatus);
                throw new PaymentReviewRequiredException();
            }
            RefundResult result =
                    switch (cancellationStatus) {
                        case "SUCCEEDED" -> RefundResult.SUCCEEDED;
                        case "REQUESTED" -> RefundResult.REQUESTED;
                        case "FAILED" -> {
                            publishReviewRequired(orderId, "PG 환불 실패", cancellationStatus);
                            throw new PaymentReviewRequiredException();
                        }
                        default -> {
                            publishReviewRequired(orderId, "알 수 없는 PG 환불 상태", cancellationStatus);
                            throw new PaymentReviewRequiredException();
                        }
                    };
            log.info("[PortOne] 환불 응답 orderId={} amount={} status={}", orderId, amount, cancellationStatus);
            return result;
        } catch (PaymentReviewRequiredException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[PortOne] 환불 실패 orderId={}: {}", orderId, ex.getMessage());
            throw new PaymentGatewayUnavailableException(orderId, ex);
        }
    }

    @Override
    public boolean isFullyRefunded(String orderId, long amount) {
        try {
            Map<?, ?> payment = fetchPayment(orderId);
            String status = normalizedText(payment.get("status"));
            String validationFailure = findPaymentValidationFailure(payment, orderId, amount);
            if (validationFailure != null) {
                publishReviewRequired(orderId, "환불 확정 전 " + validationFailure, status);
                throw new PaymentReviewRequiredException();
            }
            if ("CANCELLED".equals(status) && extractCancelledTotal(payment) != amount) {
                publishReviewRequired(orderId, "전액 환불 금액 불일치 또는 누락", status);
                throw new PaymentReviewRequiredException();
            }
            return "CANCELLED".equals(status);
        } catch (PaymentReviewRequiredException ex) {
            throw ex;
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
            String status = normalizedText(cancellation.get("status"));
            return status == null ? "UNKNOWN" : status;
        }
        return "UNKNOWN";
    }

    private static long extractCancellationTotal(Map<?, ?> response) {
        if (response != null && response.get("cancellation") instanceof Map<?, ?> cancellation) {
            return number(cancellation.get("totalAmount"));
        }
        return -1;
    }

    private static long extractPaidTotal(Map<?, ?> payment) {
        Object amountObj = payment.get("amount");
        if (amountObj instanceof Map<?, ?> amountMap) {
            return number(amountMap.get("total"));
        }
        return -1;
    }

    private static long extractCancelledTotal(Map<?, ?> payment) {
        Object amountObj = payment.get("amount");
        if (amountObj instanceof Map<?, ?> amountMap) {
            return number(amountMap.get("cancelled"));
        }
        return -1;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : -1;
    }

    /**
     * PAID 원장을 주문 상태에 반영하기 전에 상점·채널·통화·결제수단을 모두 고정한다.
     *
     * <p>PortOne V2 결제 조회 응답은 선택 채널을 {@code channel}, 결제수단을 {@code method}에
     * 담는다. 스키마가 바뀌거나 필드가 누락된 경우에도 승인하지 않고 수동 검토로 보낸다.
     */
    private String findPaymentValidationFailure(Map<?, ?> payment, String expectedPaymentId, long expectedAmount) {
        String provenanceFailure = findPaymentProvenanceFailure(payment, expectedPaymentId, expectedAmount);
        if (provenanceFailure != null) {
            return provenanceFailure;
        }
        if (!(payment.get("channel") instanceof Map<?, ?> channel)) {
            return "결제 채널 정보 누락";
        }
        if (!expectedChannelKey.equals(text(channel.get("key")))) {
            return "결제 채널 키 불일치 또는 누락";
        }
        if (!expectedChannelType.equals(normalizedText(channel.get("type")))) {
            return "결제 채널 유형 불일치 또는 누락";
        }
        if (!(payment.get("method") instanceof Map<?, ?> method)) {
            return "결제수단 정보 누락";
        }
        if (!"EASY_PAY".equals(normalizedText(method.get("type")))) {
            return "결제수단 유형 불일치 또는 누락";
        }
        if (!"KAKAOPAY".equals(normalizedText(method.get("provider")))) {
            return "간편결제 제공자 불일치 또는 누락";
        }
        return null;
    }

    private String findPaymentProvenanceFailure(Map<?, ?> payment, String expectedPaymentId, long expectedAmount) {
        if (!expectedPaymentId.equals(text(payment.get("id")))) {
            return "결제 ID 불일치 또는 누락";
        }
        if (!expectedStoreId.equals(text(payment.get("storeId")))) {
            return "상점 ID 불일치 또는 누락";
        }
        if (!"V2".equals(normalizedText(payment.get("version")))) {
            return "결제 API 버전 불일치 또는 누락";
        }
        if (!"KRW".equals(text(payment.get("currency")))) {
            return "결제 통화 불일치 또는 누락";
        }
        if (extractPaidTotal(payment) != expectedAmount) {
            return "결제 금액 불일치 또는 누락";
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private static String normalizedText(Object value) {
        String text = text(value);
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private void publishReviewRequired(String orderId, String reason, String pgStatus) {
        operationalEvents.publish(new OperationalEvent(
                Category.PAYMENT,
                Level.ERROR,
                "결제 수동 확인 필요",
                "주문 상태를 변경하지 않고 보존했습니다. PortOne 대시보드와 관리자 주문 화면에서 확인하세요.",
                Map.of("주문 ID", orderId, "사유", reason, "PG 상태", pgStatus == null ? "누락" : pgStatus),
                Instant.now()));
    }
}
