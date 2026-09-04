package com.gole.api.launch.adapter.in.web;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 거래 모델에 맞지 않는 요청을 서버에서 막는다.
 *
 * <p>두 층으로 판단한다.
 *
 * <ol>
 *   <li><b>직거래 단계({@code DIRECT_CHAT}, 0~1단계)</b> — 새 주문과 새 결제만 닫는다.
 *       단계 하향 전에 시작된 주문의 조회·배송·환불·분쟁·구매확정은 계속 열어 둔다.
 *   <li><b>플랫폼 결제 단계</b> — 주문 API는 열되, 결제 기능이 override 로 닫혀 있으면 새 결제를
 *       만드는 요청만 거부한다.
 * </ol>
 *
 * <p>주문 컨트롤러를 고치지 않고 인터셉터로 분리한 이유는 두 가지다. 결제 개방 판정이 주문
 * 도메인의 규칙이 아니라 운영 설정이라는 것, 그리고 컨트롤러를 건드리면 동시에 진행 중인
 * 정산 작업과 충돌한다는 것이다.
 *
 * <p><b>막지 않는 것.</b> PortOne 웹훅({@code /api/v1/payments/**})은 어떤 단계에서도 막지 않는다.
 * 막으면 이미 승인된 결제가 주문에 반영되지 않아 돈만 빠져나간 상태가 된다. 단계를 내리기
 * 전에 생긴 거래는 여전히 정산·환불되어야 하므로, 이 게이트는 "새 거래를 그만 받는" 장치이지
 * 진행 중인 거래를 인질로 잡는 장치가 아니다.
 */
@Component
public class LaunchGateInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LaunchGateInterceptor.class);

    private final GetLaunchConfigUseCase launchConfig;

    public LaunchGateInterceptor(GetLaunchConfigUseCase launchConfig) {
        this.launchConfig = launchConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        LaunchConfig config = launchConfig.current();
        WriteOperation operation = writeOperation(request);

        if (operation == WriteOperation.UNCLASSIFIED) {
            log.error(
                    "[launch gate] 분류되지 않은 쓰기 요청을 안전 거부함 method={} pattern={}",
                    request.getMethod(),
                    matchedPattern(request));
            throw new ForbiddenException("LAUNCH_GATE_UNCLASSIFIED", "운영 단계가 확인되지 않은 요청입니다");
        }

        if (!config.platformHandlesMoney() && operation == WriteOperation.PAYMENT_CREATION) {
            log.warn(
                    "[launch gate] 직거래 단계에서 신규 주문/결제 요청 거부 method={} pattern={} stage={}",
                    request.getMethod(),
                    matchedPattern(request),
                    config.stage().level());
            throw new ForbiddenException("LAUNCH_DIRECT_TRADE_ONLY", "지금은 직거래 단계라 주문·결제·정산 기능을 제공하지 않습니다");
        }

        if (operation == WriteOperation.PAYMENT_CREATION && !config.isEnabled(LaunchFeature.PAYMENTS)) {
            log.warn(
                    "[launch gate] 결제가 닫힌 상태에서 거래 요청 거부 method={} pattern={}",
                    request.getMethod(),
                    matchedPattern(request));
            throw new ForbiddenException("LAUNCH_PAYMENTS_CLOSED", "지금은 결제를 받지 않습니다");
        }
        if (operation == WriteOperation.REVIEW_CREATION && !config.isEnabled(LaunchFeature.REVIEWS)) {
            log.warn(
                    "[launch gate] 후기가 닫힌 상태에서 작성 요청 거부 method={} pattern={}",
                    request.getMethod(),
                    matchedPattern(request));
            throw new ForbiddenException("LAUNCH_REVIEWS_CLOSED", "거래 후기는 아직 열리지 않았습니다");
        }
        return true;
    }

    /**
     * Spring이 컨트롤러를 고른 뒤 기록한 canonical pattern으로만 쓰기 종류를 판정한다.
     *
     * <p>{@link HttpServletRequest#getRequestURI()}는 matrix parameter({@code ;x=1})를 보존하지만
     * Spring PathPattern은 이를 제거하고 같은 컨트롤러로 보낼 수 있다. 원문 URI를 보안 판정에
     * 쓰면 게이트와 실제 handler가 서로 다른 경로를 본다. 새 POST mapping은 명시적으로
     * 분류할 때까지 {@link WriteOperation#UNCLASSIFIED}로 닫힌다.
     */
    private static WriteOperation writeOperation(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return WriteOperation.NONE;
        }
        return switch (matchedPattern(request)) {
            case "/api/v1/orders", "/api/v1/orders/{orderId}/payment" -> WriteOperation.PAYMENT_CREATION;
            case "/api/v1/reviews", "/api/v1/reviews/{reviewId}/reply" -> WriteOperation.REVIEW_CREATION;
            case "/api/v1/orders/{orderId}/completion",
                    "/api/v1/orders/{orderId}/refund",
                    "/api/v1/orders/{orderId}/dispute",
                    "/api/v1/orders/{orderId}/shipment/tracking" -> WriteOperation.EXISTING_TRANSACTION;
            default -> WriteOperation.UNCLASSIFIED;
        };
    }

    private static String matchedPattern(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (value == null) {
            return "";
        }
        String pattern = value.toString();
        return pattern.length() <= 256 ? pattern : "";
    }

    private enum WriteOperation {
        NONE,
        PAYMENT_CREATION,
        REVIEW_CREATION,
        EXISTING_TRANSACTION,
        UNCLASSIFIED
    }
}
