package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.common.exception.ConflictException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 신규 소셜 방과 기존 매물 방을 하나의 읽기 모델로 합치는 Mongo 어댑터. */
@Component
public class MongoSocialChatRoomAdapter implements SocialChatRoomRepositoryPort {

    private final SocialChatRoomMongoRepository socialRooms;
    private final LegacyChatRoomMongoRepository legacyRooms;
    private final MongoTemplate mongoTemplate;

    public MongoSocialChatRoomAdapter(
            SocialChatRoomMongoRepository socialRooms,
            LegacyChatRoomMongoRepository legacyRooms,
            MongoTemplate mongoTemplate) {
        this.socialRooms = socialRooms;
        this.legacyRooms = legacyRooms;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<SocialChatRoom> findById(String roomId) {
        return socialRooms
                .findById(roomId)
                .map(MongoSocialChatRoomAdapter::toDomain)
                .or(() -> legacyRooms.findById(roomId).map(MongoSocialChatRoomAdapter::toDomain));
    }

    @Override
    public List<SocialChatRoom> findByIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        Map<String, SocialChatRoom> unique = new LinkedHashMap<>();
        socialRooms.findAllById(roomIds).stream()
                .map(MongoSocialChatRoomAdapter::toDomain)
                .forEach(room -> unique.put(room.id(), room));
        legacyRooms.findAllById(roomIds).stream()
                .map(MongoSocialChatRoomAdapter::toDomain)
                .forEach(room -> unique.putIfAbsent(room.id(), room));
        return List.copyOf(unique.values());
    }

    @Override
    public Optional<SocialChatRoom> findByDedupeKey(String dedupeKey) {
        return socialRooms.findByDedupeKey(dedupeKey).map(MongoSocialChatRoomAdapter::toDomain);
    }

    @Override
    public List<SocialChatRoom> findByMember(String accountId, int limit) {
        int safeLimit = Math.clamp(limit, 1, 100);
        var page =
                PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("createdAt")));

        Map<String, SocialChatRoom> unique = new LinkedHashMap<>();
        socialRooms.findByMemberIdsContaining(accountId, page).stream()
                .map(MongoSocialChatRoomAdapter::toDomain)
                .forEach(room -> unique.put(room.id(), room));
        legacyRooms.findMine(accountId, page).stream()
                .map(MongoSocialChatRoomAdapter::toDomain)
                .forEach(room -> unique.putIfAbsent(room.id(), room));

        // 화면은 신규 소셜 방과 레거시 매물 방을 각각 최대 safeLimit개 노출한다.
        // 합친 뒤 다시 자르면 화면에는 있는데 unread 집계에서 빠지는 방이 생긴다.
        return unique.values().stream()
                .sorted(Comparator.comparing(SocialChatRoom::lastMessageAt).reversed())
                .toList();
    }

    @Override
    public List<SocialChatRoom> findSocialByMember(String accountId, int limit) {
        int safeLimit = Math.clamp(limit, 1, 100);
        var page = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return socialRooms.findByMemberIdsContaining(accountId, page).stream()
                .map(MongoSocialChatRoomAdapter::toDomain)
                .toList();
    }

    @Override
    public void touchActivity(String roomId, java.time.Instant occurredAt) {
        Query byId = Query.query(Criteria.where("_id").is(roomId));
        Update activity = new Update().max("lastMessageAt", occurredAt).inc("version", 1L);
        var result = mongoTemplate.updateFirst(byId, activity, SocialChatRoomDocument.class);
        if (result.getMatchedCount() == 0) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(roomId)),
                    new Update().max("lastMessageAt", occurredAt),
                    "chat_rooms");
        }
    }

    @Override
    public SocialChatRoom save(SocialChatRoom room) {
        if (room.type() == ChatRoomType.LISTING) {
            throw new IllegalArgumentException("레거시 매물 방은 기존 저장 경로에서만 수정할 수 있습니다");
        }
        try {
            return toDomain(socialRooms.save(toDocument(room)));
        } catch (DuplicateKeyException | OptimisticLockingFailureException duplicate) {
            throw new ConflictException("CHAT_ROOM_ALREADY_EXISTS", "이미 만들어진 대화방입니다");
        }
    }

    private static SocialChatRoomDocument toDocument(SocialChatRoom room) {
        return new SocialChatRoomDocument(
                room.id(),
                room.type().name(),
                room.memberIds(),
                room.ownerId(),
                room.title(),
                room.dedupeKey(),
                room.createdAt(),
                room.lastMessageAt(),
                room.closedAt(),
                room.version());
    }

    private static SocialChatRoom toDomain(SocialChatRoomDocument document) {
        return new SocialChatRoom(
                document.getId(),
                ChatRoomType.valueOf(document.getType()),
                document.getMemberIds(),
                document.getOwnerId(),
                document.getTitle(),
                null,
                document.getCreatedAt(),
                document.getLastMessageAt(),
                document.getClosedAt(),
                document.getVersion());
    }

    private static SocialChatRoom toDomain(LegacyChatRoomView document) {
        return new SocialChatRoom(
                document.getId(),
                ChatRoomType.LISTING,
                List.of(document.getBuyerId(), document.getSellerId()),
                null,
                null,
                document.getListingId(),
                document.getCreatedAt(),
                document.getLastMessageAt(),
                null,
                0L);
    }
}
