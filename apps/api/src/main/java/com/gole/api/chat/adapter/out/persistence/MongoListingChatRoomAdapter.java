package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.ListingChatRoomRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 매물 채팅방({@code chat_rooms}) 영속성 어댑터. 직거래 확인·완료는 조건부 갱신으로만 기록한다 — 조건이 맞지 않으면
 * 아무것도 바꾸지 않으므로 동시 요청이 겹쳐도 한 번만 적용된다.
 */
@Component
public class MongoListingChatRoomAdapter implements ListingChatRoomRepositoryPort {

    private static final String BUYER_CONFIRMED_AT = "buyerConfirmedAt";
    private static final String SELLER_CONFIRMED_AT = "sellerConfirmedAt";
    private static final String DIRECT_TRADE_COMPLETED_AT = "directTradeCompletedAt";

    private final ChatRoomMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public MongoListingChatRoomAdapter(ChatRoomMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<ChatRoom> findById(String roomId) {
        return repository.findById(roomId).map(MongoListingChatRoomAdapter::toDomain);
    }

    @Override
    public Optional<ChatRoom> findByParticipants(String buyerId, String sellerId, String listingId) {
        return repository
                .findByBuyerIdAndSellerIdAndListingId(buyerId, sellerId, listingId)
                .map(MongoListingChatRoomAdapter::toDomain);
    }

    @Override
    public List<ChatRoom> findRecentByParticipant(String accountId, int limit) {
        return repository
                .findByBuyerIdOrSellerId(
                        accountId, accountId, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastMessageAt")))
                .stream()
                .map(MongoListingChatRoomAdapter::toDomain)
                .toList();
    }

    @Override
    public ChatRoom createOrGetExisting(ChatRoom room) {
        ChatRoomDocument document =
                new ChatRoomDocument(room.id(), room.listingId(), room.buyerId(), room.sellerId(), room.createdAt());
        try {
            return toDomain(repository.save(document));
        } catch (DuplicateKeyException concurrentCreation) {
            return findByParticipants(room.buyerId(), room.sellerId(), room.listingId())
                    .orElseThrow(() -> concurrentCreation);
        }
    }

    @Override
    public boolean recordConfirmation(String roomId, ChatRoom.Party party, Instant confirmedAt) {
        String field = confirmationField(party);
        return mongoTemplate
                        .updateFirst(
                                Query.query(Criteria.where("_id")
                                        .is(roomId)
                                        .and(field)
                                        .is(null)
                                        .and(DIRECT_TRADE_COMPLETED_AT)
                                        .is(null)),
                                Update.update(field, confirmedAt),
                                ChatRoomDocument.class)
                        .getModifiedCount()
                == 1L;
    }

    @Override
    public Optional<ChatRoom> completeIfBothConfirmed(String roomId, Instant completedAt) {
        ChatRoomDocument completed = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id")
                        .is(roomId)
                        .and(BUYER_CONFIRMED_AT)
                        .ne(null)
                        .and(SELLER_CONFIRMED_AT)
                        .ne(null)
                        .and(DIRECT_TRADE_COMPLETED_AT)
                        .is(null)),
                Update.update(DIRECT_TRADE_COMPLETED_AT, completedAt),
                FindAndModifyOptions.options().returnNew(true),
                ChatRoomDocument.class);
        return Optional.ofNullable(completed).map(MongoListingChatRoomAdapter::toDomain);
    }

    @Override
    public void clearConfirmation(String roomId, ChatRoom.Party party) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(roomId)
                        .and(DIRECT_TRADE_COMPLETED_AT)
                        .is(null)),
                new Update().unset(confirmationField(party)),
                ChatRoomDocument.class);
    }

    private static String confirmationField(ChatRoom.Party party) {
        return party == ChatRoom.Party.BUYER ? BUYER_CONFIRMED_AT : SELLER_CONFIRMED_AT;
    }

    static ChatRoom toDomain(ChatRoomDocument document) {
        return new ChatRoom(
                document.getId(),
                document.getListingId(),
                document.getBuyerId(),
                document.getSellerId(),
                document.getCreatedAt(),
                document.getLastMessageAt(),
                document.getBuyerConfirmedAt(),
                document.getSellerConfirmedAt(),
                document.getDirectTradeCompletedAt());
    }
}
