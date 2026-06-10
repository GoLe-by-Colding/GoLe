package com.gole.api.review.application.port.out;

import java.util.Optional;

/**
 * Outbound port (CROSS-CONTEXT): 후기 컨텍스트가 주문 컨텍스트의 주문 정보를 조회한다.
 * 후기 작성 자격(완료 여부/구매자 일치)을 판단하는 데 필요한 최소 정보만 노출한다. (요구사항 R2)
 *
 * <p>구현 어댑터는 주문 컨텍스트의 인바운드 유스케이스를 호출하며, 주문의 내부 도메인/영속성에는 접근하지 않는다.
 */
public interface OrderQueryPort {

    /** 주문 스냅샷 조회. 주문이 없으면 비어있음. */
    Optional<OrderSnapshot> findById(String orderId);

    /** 후기 자격 판단에 필요한 주문 최소 정보. */
    record OrderSnapshot(String orderId, String buyerId, String sellerId, boolean completed) {}
}
