package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;

/** Inbound port: 모든 방 유형이 공유하는 메시지 이력·전송. */
public interface ChatMessagingUseCase {

    /** 가장 오래 본 메시지 앞쪽을 {@code sentAt + id} 커서로 가져온다(오래된 순). 커서 없으면 최근 메시지. */
    List<ChatMessage> history(String roomId, String actorId, Instant beforeSentAt, String beforeId, int limit);

    /** SSE 재연결 시 마지막으로 받은 메시지 다음부터 재생한다. */
    List<ChatMessage> after(String roomId, String actorId, String afterId, int limit);

    /** 사용자 메시지. 운영팀 문의방이 아니면 보내는 사람의 현재 제3자 제공 동의를 먼저 확인한다. */
    ChatMessage sendFromUser(String roomId, String actorId, String content);

    /** 관리자 문의 답변. 감사가 묶이는 관리자 경로에서만 부른다. */
    ChatMessage sendAdminSupport(String roomId, String adminId, String content);
}
