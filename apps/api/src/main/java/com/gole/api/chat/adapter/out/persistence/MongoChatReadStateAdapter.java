package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.UpdateOptions;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Mongo 단일 문서 CAS와 한 번의 집계로 읽음 커서·안 읽음 수를 처리한다. */
@Component
public class MongoChatReadStateAdapter implements ChatReadStatePort {

    private final ChatReadCursorMongoRepository cursors;
    private final MongoTemplate mongoTemplate;

    public MongoChatReadStateAdapter(ChatReadCursorMongoRepository cursors, MongoTemplate mongoTemplate) {
        this.cursors = cursors;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<String, Long> countUnread(String accountId, List<String> roomIds) {
        List<String> distinctRoomIds = roomIds.stream().distinct().toList();
        if (distinctRoomIds.isEmpty()) {
            return Map.of();
        }

        Map<String, ChatReadCursorDocument> byRoom =
                cursors.findByAccountIdAndRoomIdIn(accountId, distinctRoomIds).stream()
                        .collect(Collectors.toMap(ChatReadCursorDocument::getRoomId, Function.identity()));
        Criteria[] roomBranches = distinctRoomIds.stream()
                .map(roomId -> afterCursor(roomId, byRoom.get(roomId)))
                .toArray(Criteria[]::new);
        Criteria match = new Criteria()
                .andOperator(Criteria.where("senderId").ne(accountId), new Criteria().orOperator(roomBranches));
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(match), Aggregation.group("roomId").count().as("count"));

        Map<String, Long> result = new HashMap<>();
        for (Document row : mongoTemplate
                .aggregate(aggregation, "chat_messages", Document.class)
                .getMappedResults()) {
            Object count = row.get("count");
            if (row.getString("_id") != null && count instanceof Number number) {
                result.put(row.getString("_id"), number.longValue());
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public void advance(
            String roomId, String accountId, String lastReadMessageId, Instant lastReadSentAt, Instant updatedAt) {
        String cursorId = cursorId(roomId, accountId);
        try {
            writeMaximumCursor(cursorId, roomId, accountId, lastReadMessageId, lastReadSentAt, updatedAt, true);
        } catch (DuplicateKeyException | MongoWriteException concurrentFirstInsert) {
            if (concurrentFirstInsert instanceof MongoWriteException mongoWrite
                    && mongoWrite.getError().getCode() != 11000) {
                throw mongoWrite;
            }
            // 최초 커서 두 건이 동시에 insert한 경우 승자가 만든 문서에 같은 max 연산을
            // 다시 적용한다. 이 경로는 일반 읽음 요청에서만 발생하며 invite 트랜잭션은
            // 방 버전 CAS를 먼저 통과한 한 요청만 여기까지 도달한다.
            writeMaximumCursor(cursorId, roomId, accountId, lastReadMessageId, lastReadSentAt, updatedAt, false);
        }
    }

    private void writeMaximumCursor(
            String cursorId,
            String roomId,
            String accountId,
            String lastReadMessageId,
            Instant lastReadSentAt,
            Instant updatedAt,
            boolean upsert) {
        Date incomingSentAt = Date.from(lastReadSentAt);
        Document currentMissing = new Document("$eq", List.of(new Document("$type", "$lastReadSentAt"), "missing"));
        Document incomingIsLater = new Document(
                "$or",
                List.of(
                        currentMissing,
                        new Document("$lt", List.of("$lastReadSentAt", incomingSentAt)),
                        new Document(
                                "$and",
                                List.of(
                                        new Document("$eq", List.of("$lastReadSentAt", incomingSentAt)),
                                        new Document("$lt", List.of("$lastReadMessageId", lastReadMessageId))))));
        Document chooseMessageId =
                new Document("$cond", List.of(incomingIsLater, lastReadMessageId, "$lastReadMessageId"));
        Document chooseSentAt = new Document("$cond", List.of(incomingIsLater, incomingSentAt, "$lastReadSentAt"));
        Document chooseUpdatedAt = new Document("$cond", List.of(incomingIsLater, Date.from(updatedAt), "$updatedAt"));
        Document fields = new Document("roomId", roomId)
                .append("accountId", accountId)
                .append("lastReadMessageId", chooseMessageId)
                .append("lastReadSentAt", chooseSentAt)
                .append("updatedAt", chooseUpdatedAt);

        mongoTemplate
                .getCollection(mongoTemplate.getCollectionName(ChatReadCursorDocument.class))
                .updateOne(
                        new Document("_id", cursorId),
                        List.of(new Document("$set", fields)),
                        new UpdateOptions().upsert(upsert));
    }

    @Override
    public void initializeAtLatest(String roomId, String accountId, Instant updatedAt) {
        Query latestQuery = Query.query(Criteria.where("roomId").is(roomId))
                .with(Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id")))
                .limit(1);
        ChatMessageDocument latest = mongoTemplate.findOne(latestQuery, ChatMessageDocument.class);
        if (latest != null) {
            advance(roomId, accountId, latest.getId(), latest.getSentAt(), updatedAt);
        }
    }

    private static Criteria afterCursor(String roomId, ChatReadCursorDocument cursor) {
        Criteria room = Criteria.where("roomId").is(roomId);
        if (cursor == null) {
            return room;
        }
        Criteria after = new Criteria()
                .orOperator(
                        Criteria.where("sentAt").gt(cursor.getLastReadSentAt()),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("sentAt").is(cursor.getLastReadSentAt()),
                                        Criteria.where("_id").gt(cursor.getLastReadMessageId())));
        return new Criteria().andOperator(room, after);
    }

    private static String cursorId(String roomId, String accountId) {
        return UUID.nameUUIDFromBytes((roomId + "\u0000" + accountId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
