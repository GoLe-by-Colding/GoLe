package com.gole.api.order.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Inbound port: 운영 화면이 보는 주문 현황. 읽기 전용이며 주문 불변식과 무관한 리포팅 값이다.
 *
 * <p>상태는 저장된 이름 그대로 문자열로 낸다. 알 수 없는 값이 있어도 운영 화면은 보여 줘야 하기 때문이다.
 */
public interface MonitorOrdersUseCase {

    /** 상태별 주문 수와 완료 주문 거래액(GMV). */
    OrderStats orderStats();

    /** 최근 주문. status 가 null 이거나 비면 전체, query 는 주문·구매자·판매자 ID와 세트 번호의 부분 일치. */
    List<OrderMonitorRow> recentOrders(String status, String query, int limit);

    /** 전체 주문 수(추정치). */
    long estimatedOrderCount();

    record OrderStats(Map<String, Long> countByStatus, long completedGmv) {}

    /**
     * @param paymentMethodType 결제수단 분류. 결제 전이거나 분류가 없으면 {@code null}
     */
    record OrderMonitorRow(
            String id,
            String status,
            long amount,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String paymentMethodType,
            String paymentProvider,
            Instant createdAt) {}
}
