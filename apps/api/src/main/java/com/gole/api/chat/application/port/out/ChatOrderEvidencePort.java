package com.gole.api.chat.application.port.out;

/** Outbound port: 주문 증거. 진행 중인 주문이 있는 계정의 문의 대화는 분쟁 증거로 남겨 둔다. */
public interface ChatOrderEvidencePort {

    /** 구매자나 판매자로 참여한 주문 중 종결되지 않은 것이 있는가. */
    boolean hasUnsettledOrder(String accountId);
}
