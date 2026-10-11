package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: 1:1·그룹·문의 대화방과 차단. 새 대화 상대에게 내 정보를 내보이는 행동(1:1 시작·그룹 생성·초대)은 관련된 모든 사람의
 * 현재 제3자 제공 동의를 확인한 뒤에만 한다.
 */
public interface SocialChatUseCase {

    List<SocialChatRoom> mySocialRooms(String actorId, int limit);

    /** 같은 상대와의 1:1 방이 있으면 그대로 연다(동의 철회 뒤에도 재입장 가능). 없으면 양쪽 동의를 확인하고 만든다. */
    SocialChatRoom startDirect(String actorId, String peerId);

    SocialChatRoom startGroup(String actorId, String title, List<String> memberIds);

    /** 이미 멤버면 그대로 돌려준다. 아니면 초대자·기존 멤버·초대 대상의 동의를 확인하고 초대한다. */
    SocialChatRoom inviteMember(String roomId, String actorId, String inviteeId);

    SocialChatRoom leave(String roomId, String actorId);

    void block(String actorId, String targetId, String reason);

    void unblock(String actorId, String targetId);

    List<String> myBlockedAccountIds(String actorId);

    /** 읽을 수 있는 방이면 돌려주고 아니면 거부한다. 문의방은 문의자와 현재 담당자만 읽는다. */
    SocialChatRoom requireReadable(String roomId, String actorId);

    SocialChatRoom requireRoom(String roomId);

    Optional<SupportTicket> supportTicketOf(String roomId);

    /** 방 목록에 붙일 문의 상태를 한 번에 읽는다(N+1 방지). */
    List<SupportTicket> supportTicketsOf(List<String> roomIds);
}
