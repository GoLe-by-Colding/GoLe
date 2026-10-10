package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.ChatRoom;

/**
 * Inbound port: 채팅 직거래 완료 확인·취소. 매물 대화방 참여자만, 판매자 신원확인이 된 매물에서만 확인할 수 있다. 양쪽이 확인하면
 * 매물이 판매 완료로 바뀐다.
 */
public interface DirectTradeUseCase {

    ChatRoom confirm(String roomId, String actorId);

    ChatRoom cancelConfirmation(String roomId, String actorId);
}
