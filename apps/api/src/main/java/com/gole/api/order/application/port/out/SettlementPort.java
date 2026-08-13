package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.Settlement;

/**
 * Outbound port: 정산. 완료 주문 1건당 정확히 1회(exactly-once) 정산을 보장한다.
 * (요구사항 13.4, 13.5)
 *
 * <p>계산 결과인 {@link Settlement} 전표를 <b>반환</b>한다. 어댑터가 스스로 저장하지 않는 이유는
 * 정산이 주문 애그리거트의 일부이기 때문이다. 어댑터가 따로 쓰면 뒤이은
 * {@code orderRepository.save(order)}가 그 결과를 덮어쓴다. 호출자가 전표를 주문에 붙여
 * <b>한 번의 저장</b>으로 함께 커밋한다.
 */
public interface SettlementPort {

    Settlement settleOnce(String orderId, String sellerId, long amount);
}
