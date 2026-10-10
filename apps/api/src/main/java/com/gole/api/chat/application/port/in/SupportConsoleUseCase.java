package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportInternalNote;
import com.gole.api.chat.domain.model.SupportOperator;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import java.util.List;

/**
 * Inbound port: 운영자 문의 콘솔 — 인박스·배정·이관·인수·해결·재개·답변·내부 메모. 본문은 담당자로 배정된 뒤에만 읽는다.
 *
 * <p>상태를 바꾸는 조치는 실제로 바뀌었을 때 관리자 감사 로그를 같은 트랜잭션에서 남긴다. 그래서 조치를 하는
 * 메서드는 감사에 남길 운영자({@link SupportOperator})를 받는다.
 */
public interface SupportConsoleUseCase {

    List<SupportTicket> inbox(String adminId, SupportStatus status, SupportCategory category, int limit);

    /** 관리자 작업 배지에 쓸 미배정 문의 수. */
    long countUnassigned();

    SupportConversation assignToSelf(String roomId, SupportOperator operator);

    SupportConversation transfer(String roomId, SupportOperator operator, String targetAdminId);

    SupportTakeover takeOver(String roomId, SupportOperator operator, String rawReason);

    SupportTransition resolve(String roomId, SupportOperator operator);

    SupportTransition reopen(String roomId, SupportOperator operator);

    /** 담당자만 답한다. 담당 확인·메시지 저장·감사 기록이 한 트랜잭션이다. */
    ChatMessage reply(String roomId, SupportOperator operator, String content);

    void addNote(String roomId, SupportOperator operator, String note);

    List<SupportInternalNote> notes(String roomId, String actorId, int limit);

    SupportTicket requireAssignedTo(String roomId, String actorId);

    record SupportConversation(SocialChatRoom room, SupportTicket ticket, boolean changed) {}

    record SupportTransition(SupportTicket ticket, boolean changed) {}

    record SupportTakeover(SocialChatRoom room, SupportTicket ticket, String previousAssigneeId, String reason) {}
}
