package com.gole.api.offer.adapter.out.chat;

import com.gole.api.chat.application.port.in.ListingRoomAccessUseCase;
import com.gole.api.chat.application.port.in.ListingRoomAccessUseCase.ListingRoomParticipants;
import com.gole.api.chat.application.port.in.PostChatMessageUseCase;
import com.gole.api.offer.application.port.out.OfferChatPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 채팅 컨텍스트 통합 어댑터. 제안의 {@link OfferChatPort}를 채팅 인바운드 포트 두 개로 위임 구현한다.
 *
 * <p>의존 방향은 offer → chat 하나다. 채팅은 제안을 모른다.
 */
@Component
public class OfferChatAdapter implements OfferChatPort {

    private final ListingRoomAccessUseCase roomAccess;
    private final PostChatMessageUseCase messages;

    public OfferChatAdapter(ListingRoomAccessUseCase roomAccess, PostChatMessageUseCase messages) {
        this.roomAccess = roomAccess;
        this.messages = messages;
    }

    @Override
    public Optional<ListingRoom> requireSendableListingRoom(String roomId, String actorId) {
        return roomAccess.requireSendableListingRoom(roomId, actorId).map(OfferChatAdapter::toListingRoom);
    }

    @Override
    public Optional<ListingRoom> requireReadableListingRoom(String roomId, String actorId) {
        return roomAccess.requireReadableListingRoom(roomId, actorId).map(OfferChatAdapter::toListingRoom);
    }

    @Override
    public void post(String roomId, String actorId, String content) {
        messages.post(roomId, actorId, content);
    }

    private static ListingRoom toListingRoom(ListingRoomParticipants room) {
        return new ListingRoom(room.roomId(), room.listingId(), room.buyerId(), room.sellerId());
    }
}
