package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import java.util.List;

/** Inbound port: 매물 채팅방 개설·목록과 방 단건 해석. */
public interface ListingChatRoomUseCase {

    /**
     * 이 매물의 구매자↔판매자 방을 연다. 이미 있으면 그대로(매물이 숨겨진 뒤에도) 돌려준다. 새로 만들 때는 판매자 신원확인, 구매자·판매자의
     * 현재 제3자 제공 동의, 공개 매물 여부를 확인한다. 자기 매물에는 열지 않는다.
     */
    ChatRoom open(String buyerId, String listingId);

    /** 참여 중인 매물 방을 마지막 활동이 최근인 순으로 최대 100개. */
    List<ChatRoom> myRooms(String actorId);

    /** 읽을 수 있는 방 하나를 해석한다(알림 딥링크용). 매물 방이면 매물 방, 아니면 소셜 방과 문의 상태. */
    ResolvedChatRoom resolve(String roomId, String actorId);

    record ResolvedChatRoom(ChatRoom listingRoom, SocialChatRoom socialRoom, SupportTicket supportTicket) {

        public boolean isListing() {
            return listingRoom != null;
        }
    }
}
