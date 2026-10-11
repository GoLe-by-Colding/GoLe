package com.gole.api.order.application.port.in;

/**
 * Inbound port: 회원 탈퇴 때 주문 컨텍스트가 맡는 일. (account-deletion-participants D1)
 *
 * <p>아직 파기하면 안 되는 기록이 남았는지만 답한다. 이 컨텍스트의 기록은 보존 대상이라 탈퇴 때 지우지 않는다. 호출자의
 * 트랜잭션 안에서 돌아 계정 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface OrderAccountErasureUseCase {

    /** 결제 대기·자금 보유·분쟁·환불 대기 중인 주문의 당사자인가. */
    boolean hasActiveOrder(String accountId);

    /** 아직 지급되지 않은 판매 정산이 있는가. */
    boolean hasUnsettledPayout(String accountId);
}
