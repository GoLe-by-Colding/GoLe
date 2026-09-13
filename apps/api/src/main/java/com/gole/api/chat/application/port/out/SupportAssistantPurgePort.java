package com.gole.api.chat.application.port.out;

/** 승인된 문의 파기에서만 호출하는 내부 분석 사본 파기 경계. 실패하면 Mongo 파기도 rollback한다. */
public interface SupportAssistantPurgePort {
    default void requireAvailable(boolean remoteCopyPossible) {}

    void purge(String roomId, String requesterId);
}
