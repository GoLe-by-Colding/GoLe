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

        if (!config.platformHandlesMoney() && createsPayment(request)) {
            log.warn(
                    "[launch gate] 직거래 단계에서 신규 주문/결제 요청 거부 method={} path={} stage={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    config.stage().level());
            throw new ForbiddenException("LAUNCH_DIRECT_TRADE_ONLY", "지금은 직거래 단계라 주문·결제·정산 기능을 제공하지 않습니다");
        }

        if (createsPayment(request) && !config.isEnabled(LaunchFeature.PAYMENTS)) {
            log.warn(
                    "[launch gate] 결제가 닫힌 상태에서 거래 요청 거부 method={} path={}",
                    request.getMethod(),
                    request.getRequestURI());
            throw new ForbiddenException("LAUNCH_PAYMENTS_CLOSED", "지금은 결제를 받지 않습니다");
        }
        if (createsReview(request) && !config.isEnabled(LaunchFeature.REVIEWS)) {
            log.warn(
                    "[launch gate] 후기가 닫힌 상태에서 작성 요청 거부 method={} path={}",
                    request.getMethod(),
                    request.getRequestURI());
            throw new ForbiddenException("LAUNCH_REVIEWS_CLOSED", "거래 후기는 아직 열리지 않았습니다");
        }
        return true;
    }

    /**
     * 돈이 새로 들어오는 요청인가.
     *
     * <p>경로를 명시 목록으로 좁힌다. 새 엔드포인트가 생겼을 때 조용히 열리는 쪽이 조용히
     * 막히는 쪽보다 낫다 — 막히면 운영자가 원인을 찾기 어렵다.
     */
    private static boolean createsPayment(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return "/api/v1/orders".equals(uri) || (uri.startsWith("/api/v1/orders/") && uri.endsWith("/payment"));
    }

    /** 기존 후기 조회는 유지하고 새 후기와 판매자 답글 작성은 운영 설정으로 함께 닫는다. */
    private static boolean createsReview(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return "/api/v1/reviews".equals(uri) || (uri.startsWith("/api/v1/reviews/") && uri.endsWith("/reply"));
    }
}
