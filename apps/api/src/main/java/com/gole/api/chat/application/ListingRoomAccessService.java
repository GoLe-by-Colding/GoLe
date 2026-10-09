package com.gole.api.chat.application;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.application.port.in.ListingRoomAccessUseCase;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 매물 방 접근 검사. 권한 판단은 {@link SocialChatService}에 맡기고, 구매자·판매자 구분은
 * {@code chat_rooms} 문서에서 읽는다({@link DirectTradeService}와 같은 출처).
 */
@Service
public class ListingRoomAccessService implements ListingRoomAccessUseCase {

    private final SocialChatService socialChats;
    private final ChatRoomMongoRepository listingRooms;

    public ListingRoomAccessService(SocialChatService socialChats, ChatRoomMongoRepository listingRooms) {
        this.socialChats = socialChats;
        this.listingRooms = listingRooms;
    }

    @Override
    public Optional<ListingRoomParticipants> requireSendableListingRoom(String roomId, String actorId) {
        return participants(socialChats.requireSendable(roomId, actorId));
    }

    @Override
    public Optional<ListingRoomParticipants> requireReadableListingRoom(String roomId, String actorId) {
        return participants(socialChats.requireReadable(roomId, actorId));
    }

    private Optional<ListingRoomParticipants> participants(SocialChatRoom room) {
        if (room.type() != ChatRoomType.LISTING) {
            return Optional.empty();
        }
        return listingRooms
                .findById(room.id())
                .filter(document -> document.getListingId() != null)
                .map(ListingRoomAccessService::toParticipants);
    }

    private static ListingRoomParticipants toParticipants(ChatRoomDocument document) {
        return new ListingRoomParticipants(
                document.getId(), document.getListingId(), document.getBuyerId(), document.getSellerId());
    }
}
