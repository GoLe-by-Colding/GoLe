package com.gole.api.chat.application.port.out;

/** 직거래 확인 상태를 상대 참여자에게 알린다. 알림 전달 실패는 거래 상태 전이를 막지 않는다. */
public interface DirectTradeNotifierPort {

    /** 한쪽이 먼저 확인해 상대방의 확인을 기다릴 때 보낸다. */
    void confirmationRequested(String recipientId, String roomId);

    /** 양쪽 확인으로 직거래가 최종 완료됐을 때 먼저 확인한 상대방에게 보낸다. */
    void tradeCompleted(String recipientId, String roomId);
}
