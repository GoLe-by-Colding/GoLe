package com.gole.api.order.application.port.in;

/**
 * Inbound port: 결제 시도 시작. 브라우저가 결제창을 열기 직전에 호출한다.
 *
 * <p>PG 결제 식별자를 <b>서버가</b> 발급하는 이유는 두 가지다. 하나, PG는 같은 식별자를 두 번
 * 받아주지 않으므로 시도마다 새 값이 필요하다. 둘, 결제 검증과 환불이 그 식별자로 이뤄지므로
 * 무엇이 유효한 식별자인지는 서버가 알고 있어야 한다. 클라이언트가 지어내면 둘 다 무너진다.
 */
public interface StartPaymentUseCase {

    /**
     * 새 결제 시도를 시작하고 결제창에 넘길 식별자를 돌려준다. 결제 대기 상태에서만 가능하다.
     */
    String start(String orderId);
}
