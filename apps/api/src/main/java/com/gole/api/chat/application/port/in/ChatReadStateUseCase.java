package com.gole.api.chat.application.port.in;

import java.util.Map;

/** Inbound port: 읽음 커서와 방별 안 읽음 수. */
public interface ChatReadStateUseCase {

    Map<String, Long> unreadCounts(String actorId);

    void markRead(String roomId, String actorId, String lastMessageId);
}
