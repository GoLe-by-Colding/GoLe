package com.gole.api.chat.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 채팅 읽음 커서와 방별 안 읽음 수를 저장·조회하는 아웃바운드 포트. */
public interface ChatReadStatePort {

    /** 요청한 방 중 상대가 보낸 커서 이후 메시지 수만 반환한다. 0인 방은 생략할 수 있다. */
    Map<String, Long> countUnread(String accountId, List<String> roomIds);

    /** {@code sentAt + messageId} 순서가 더 뒤일 때만 커서를 원자적으로 전진시킨다. */
    void advance(String roomId, String accountId, String lastReadMessageId, Instant lastReadSentAt, Instant updatedAt);

    /** 새로 초대된 멤버가 입장 전 과거 이력을 안 읽음으로 받지 않도록 현재 말단에 맞춘다. */
    void initializeAtLatest(String roomId, String accountId, Instant updatedAt);
}
