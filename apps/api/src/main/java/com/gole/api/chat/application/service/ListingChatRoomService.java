package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.ListingChatRoomUseCase;
import com.gole.api.chat.application.port.out.ChatConsentPort;
import com.gole.api.chat.application.port.out.ChatListingPort;
import com.gole.api.chat.application.port.out.ChatSellerVerificationPort;
import com.gole.api.chat.application.port.out.ListingChatRoomRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 매물 채팅방 개설·목록·단건 해석. 구매자·판매자는 서버가 정한다 — 요청 본문의 값을 믿지 않는다. */
@Service
public class ListingChatRoomService implements ListingChatRoomUseCase {

    private static final int MY_ROOMS_LIMIT = 100;

    private final ListingChatRoomRepositoryPort rooms;
    private final SocialChatService socialChats;
    private final ChatListingPort listings;
    private final ChatConsentPort consents;
    private final ChatSellerVerificationPort sellerVerification;
    private final Clock clock;

    public ListingChatRoomService(
            ListingChatRoomRepositoryPort rooms,
            SocialChatService socialChats,
            ChatListingPort listings,
            ChatConsentPort consents,
            ChatSellerVerificationPort sellerVerification,
            Clock clock) {
        this.rooms = rooms;
        this.socialChats = socialChats;
        this.listings = listings;
        this.consents = consents;
        this.sellerVerification = sellerVerification;
        this.clock = clock;
    }

    @Override
    public ChatRoom open(String buyerId, String listingId) {
        String sellerId = listings.sellerOf(listingId);
        if (buyerId.equals(sellerId)) {
            throw new ForbiddenException("CHAT_SELF_ROOM_NOT_ALLOWED", "자신의 매물에는 채팅을 시작할 수 없습니다");
        }
        socialChats.requireCanStartPrivateConversation(buyerId, sellerId);
        Optional<ChatRoom> existing = rooms.findByParticipants(buyerId, sellerId, listingId);
        if (existing.isPresent()) {
            // 철회 뒤에도 이미 참여 중인 방과 과거 대화는 계속 열 수 있다.
            return existing.get();
        }
        // 기존 방 재진입은 유지하되, 새 거래 연결은 대상 판매자의 실제 전화번호 인증과
        // 운영 준비 래치를 모두 통과해야 한다. 구매자의 인증 상태로 대신 판단하지 않는다.
        sellerVerification.requireVerifiedSeller(sellerId);
        consents.requireCurrent(buyerId);
        consents.requireCurrentSubject(sellerId);
        // 삭제 전 만들어진 거래방은 이력 보존을 위해 계속 반환하되,
        // 공개되지 않는 매물에 새 방을 만드는 것은 막는다.
        listings.requirePublic(listingId);
        return rooms.createOrGetExisting(
                ChatRoom.open(UUID.randomUUID().toString(), listingId, buyerId, sellerId, Instant.now(clock)));
    }

    @Override
    public List<ChatRoom> myRooms(String actorId) {
        return rooms.findRecentByParticipant(actorId, MY_ROOMS_LIMIT);
    }

    @Override
    public ResolvedChatRoom resolve(String roomId, String actorId) {
        SocialChatRoom readable = socialChats.requireReadable(roomId, actorId);
        if (readable.type() == ChatRoomType.LISTING) {
            ChatRoom listingRoom = rooms.findById(roomId)
                    .orElseThrow(() -> new NotFoundException("CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다"));
            return new ResolvedChatRoom(listingRoom, null, null);
        }
        SupportTicket ticket = readable.type() == ChatRoomType.SUPPORT
                ? socialChats.supportTicketOf(roomId).orElse(null)
                : null;
        return new ResolvedChatRoom(null, readable, ticket);
    }
}
