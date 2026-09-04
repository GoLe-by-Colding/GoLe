package com.gole.api.launch.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.order.adapter.in.web.OrderController;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class LaunchGateInterceptorTest {

    private final GetLaunchConfigUseCase launchConfig = mock(GetLaunchConfigUseCase.class);
    private final LaunchGateInterceptor interceptor = new LaunchGateInterceptor(launchConfig);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private void stage(LaunchStage stage) {
        when(launchConfig.current()).thenReturn(new LaunchConfig(stage, Map.of(), null, null));
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    @Test
    @DisplayName("직거래 0·1단계는 신규 주문·결제만 막고 기존 거래 후속 조치는 허용한다")
    void directTradeStagesBlockOnlyNewMoneyIn() {
        for (LaunchStage directStage : new LaunchStage[] {LaunchStage.BROWSE_ONLY, LaunchStage.PREPARING}) {
            stage(directStage);

            assertThatThrownBy(() -> interceptor.preHandle(request("POST", "/api/v1/orders"), response, new Object()))
                    .as("단계 %s의 신규 주문", directStage)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("직거래 단계");
            assertThatThrownBy(() -> interceptor.preHandle(
                            request("POST", "/api/v1/orders/order-1/payment"), response, new Object()))
                    .as("단계 %s의 신규 결제", directStage)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("직거래 단계");

            // 단계 하향 전에 시작된 거래는 조회·배송·환불·분쟁·구매확정을 계속할 수 있어야 한다.
            assertThat(interceptor.preHandle(request("GET", "/api/v1/orders/order-1"), response, new Object()))
                    .as("단계 %s의 주문 조회", directStage)
                    .isTrue();
            assertThat(interceptor.preHandle(request("GET", "/api/v1/orders/settlements"), response, new Object()))
                    .as("단계 %s의 기존 정산 조회", directStage)
                    .isTrue();
            assertThat(interceptor.preHandle(request("PUT", "/api/v1/orders/order-1/shipment"), response, new Object()))
                    .as("단계 %s의 배송 정보 등록", directStage)
                    .isTrue();
            assertThat(interceptor.preHandle(
                            request("POST", "/api/v1/orders/order-1/shipment/tracking"), response, new Object()))
                    .as("단계 %s의 배송 추적", directStage)
                    .isTrue();
            assertThat(interceptor.preHandle(request("POST", "/api/v1/orders/order-1/refund"), response, new Object()))
                    .as("단계 %s의 환불", directStage)
                    .isTrue();
            assertThat(interceptor.preHandle(request("POST", "/api/v1/orders/order-1/dispute"), response, new Object()))
                    .as("단계 %s의 분쟁", directStage)
                    .isTrue();
            assertThat(interceptor.preHandle(
                            request("POST", "/api/v1/orders/order-1/completion"), response, new Object()))
                    .as("단계 %s의 구매확정", directStage)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("주문 POST 매핑은 신규 결제 또는 기존 거래 후속 조치로 빠짐없이 분류한다")
    void orderPostMappingsRequireExplicitLaunchGateReview() {
        Map<String, Boolean> expectedMoneyInByPath = Map.of(
                "/api/v1/orders", true,
                "/api/v1/orders/{orderId}/payment", true,
                "/api/v1/orders/{orderId}/completion", false,
                "/api/v1/orders/{orderId}/refund", false,
                "/api/v1/orders/{orderId}/dispute", false);

        Set<String> actualMappings = orderPostMappings();
        assertThat(actualMappings)
                .as("새 주문 POST를 추가하면 돈이 들어오는 요청인지 후속 조치인지 게이트 분류를 검토해야 한다")
                .containsExactlyInAnyOrderElementsOf(expectedMoneyInByPath.keySet());

        stage(LaunchStage.BROWSE_ONLY);
        expectedMoneyInByPath.forEach((template, moneyIn) -> {
            String path = template.replace("{orderId}", "order-1");
            if (moneyIn) {
                assertThatThrownBy(() -> interceptor.preHandle(request("POST", path), response, new Object()))
                        .as(path)
                        .isInstanceOf(ForbiddenException.class);
            } else {
                assertThat(interceptor.preHandle(request("POST", path), response, new Object()))
                        .as(path)
                        .isTrue();
            }
        });
    }

    @Test
    @DisplayName("플랫폼 결제 단계에서는 주문 요청을 통과시킨다")
    void tradingStageAllowsOrders() {
        stage(LaunchStage.TRADING);

        assertThat(interceptor.preHandle(request("POST", "/api/v1/orders"), response, new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(request("GET", "/api/v1/orders/settlements"), response, new Object()))
                .isTrue();
    }

    @Test
    @DisplayName("결제 override 가 닫혀 있으면 새 결제만 막고 진행 중 거래는 건드리지 않는다")
    void closedPaymentsBlockOnlyNewMoneyIn() {
        when(launchConfig.current())
                .thenReturn(new LaunchConfig(LaunchStage.FULL, Map.of(LaunchFeature.PAYMENTS, false), null, null));

        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "/api/v1/orders"), response, new Object()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("결제를 받지 않습니다");
        assertThatThrownBy(() -> interceptor.preHandle(
                        request("POST", "/api/v1/orders/order-1/payment"), response, new Object()))
                .isInstanceOf(ForbiddenException.class);

        // 환불·분쟁·구매확정과 조회는 계속 열려 있어야 한다 — 이미 시작된 거래를 인질로 잡지 않는다.
        assertThat(interceptor.preHandle(request("POST", "/api/v1/orders/order-1/refund"), response, new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(request("POST", "/api/v1/orders/order-1/dispute"), response, new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(request("POST", "/api/v1/orders/order-1/completion"), response, new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(request("GET", "/api/v1/orders/order-1"), response, new Object()))
                .isTrue();
    }

    @Test
    @DisplayName("후기 기능이 닫히면 후기·답글 작성만 막고 기존 후기 조회는 허용한다")
    void closedReviewsBlockOnlyWrites() {
        when(launchConfig.current())
                .thenReturn(new LaunchConfig(LaunchStage.TRADING, Map.of(LaunchFeature.REVIEWS, false), null, null));

        assertThatThrownBy(() -> interceptor.preHandle(request("POST", "/api/v1/reviews"), response, new Object()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("후기");
        assertThatThrownBy(() -> interceptor.preHandle(
                        request("POST", "/api/v1/reviews/review-1/reply"), response, new Object()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("후기");
        assertThat(interceptor.preHandle(request("GET", "/api/v1/sellers/seller-1/reviews"), response, new Object()))
                .isTrue();
    }

    @Test
    @DisplayName("후기 기능이 열리면 작성 요청을 허용한다")
    void openReviewsAllowWrites() {
        stage(LaunchStage.TRADING);

        assertThat(interceptor.preHandle(request("POST", "/api/v1/reviews"), response, new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(request("POST", "/api/v1/reviews/review-1/reply"), response, new Object()))
                .isTrue();
    }

    @Test
    @DisplayName("CORS 프리플라이트는 설정 조회 없이 통과한다")
    void preflightPassesThrough() {
        MockHttpServletRequest preflight = request("OPTIONS", "/api/v1/orders");
        preflight.addHeader("Origin", "http://localhost:3000");
        preflight.addHeader("Access-Control-Request-Method", "POST");

        assertThat(interceptor.preHandle(preflight, response, new Object())).isTrue();
    }

    private static Set<String> orderPostMappings() {
        RequestMapping root = OrderController.class.getAnnotation(RequestMapping.class);
        String base = Stream.concat(Arrays.stream(root.value()), Arrays.stream(root.path()))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow();
        Set<String> mappings = new LinkedHashSet<>();
        Arrays.stream(OrderController.class.getDeclaredMethods()).forEach(method -> {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            if (mapping == null) {
                return;
            }
            String[] paths = Stream.concat(Arrays.stream(mapping.value()), Arrays.stream(mapping.path()))
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toArray(String[]::new);
            if (paths.length == 0) {
                mappings.add(base);
                return;
            }
            Arrays.stream(paths).map(base::concat).forEach(mappings::add);
        });
        return mappings;
    }
}
