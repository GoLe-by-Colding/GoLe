package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportInternalNote;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import java.util.List;

/** Inbound port: 운영자 문의 콘솔 — 인박스·배정·이관·인수·해결·재개·내부 메모. 본문은 담당자로 배정된 뒤에만 읽는다. */
public interface SupportConsoleUseCase {

    List<SupportTicket> inbox(String adminId, SupportStatus status, SupportCategory category, int limit);

    /** 관리자 작업 배지에 쓸 미배정 문의 수. */
    long countUnassigned();

    SupportConversation assignToSelf(String roomId, String adminId);

    SupportConversation transfer(String roomId, String actorId, String targetAdminId);

    SupportTakeover takeOver(String roomId, String actorId, String rawReason);

    SupportTransition resolve(String roomId, String actorId);

    SupportTransition reopen(String roomId, String actorId);

    void addNote(String roomId, String actorId, String note);

    List<SupportInternalNote> notes(String roomId, String actorId, int limit);

    SupportTicket requireAssignedTo(String roomId, String actorId);

    record SupportConversation(SocialChatRoom room, SupportTicket ticket, boolean changed) {}

    record SupportTransition(SupportTicket ticket, boolean changed) {}

    record SupportTakeover(SocialChatRoom room, SupportTicket ticket, String previousAssigneeId, String reason) {}
}
