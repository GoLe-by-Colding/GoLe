package com.gole.api.chat.application.port.out;

/** Outbound port: 지금 출시 단계에서 채팅 직거래 완료를 새로 받는가. */
public interface DirectTradeGatePort {

    boolean directTradeOpen();
}
