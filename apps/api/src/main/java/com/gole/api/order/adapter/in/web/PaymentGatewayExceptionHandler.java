package com.gole.api.order.adapter.in.web;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.ErrorResponse;
import com.gole.api.order.domain.exception.PaymentGatewayUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * PG 일시 장애를 503 으로 바꾼다. 결제 거절과 구분해 주문을 보존하고 재시도를 요청한다.
 *
 * <p>공통 처리기(common)는 컨텍스트 예외를 모르게 두므로 order 가 자기 예외의 응답을 맡는다. 공통 처리기의 catch-all 보다 먼저
 * 고르도록 우선순위를 가장 높게 둔다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentGatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayExceptionHandler.class);

    private final OperationalEventPublisher operationalEventPublisher;

    public PaymentGatewayExceptionHandler(OperationalEventPublisher operationalEventPublisher) {
        this.operationalEventPublisher = operationalEventPublisher;
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePaymentGatewayUnavailable(
            PaymentGatewayUnavailableException ex, HttpServletRequest request) {
        log.warn("결제 검증 일시 장애 path={}", request.getRequestURI(), ex);
        operationalEventPublisher.publish(new OperationalEvent(
                Category.PAYMENT,
                Level.ERROR,
                "결제 검증 일시 장애",
                "PG 결제 상태를 확인하지 못해 주문 상태를 보존하고 재시도를 요청했습니다.",
                Map.of("요청 경로", request.getRequestURI()),
                Instant.now()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("PAYMENT_GATEWAY_UNAVAILABLE", "결제 상태 확인이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."));
    }
}
