package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.in.ChatAccountErasureUseCase.ChatErasure;
import com.gole.api.chat.application.port.out.ChatAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 채팅 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoChatAccountErasureAdapter implements ChatAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoChatAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean hasSupportRecords(String accountId) {
        return exists("support_tickets", Criteria.where("requesterId").is(accountId));
    }

    @Override
    public boolean ownsOpenGroup(String accountId) {
        return exists(
                "social_chat_rooms",
                Criteria.where("ownerId").is(accountId).and("closedAt").is(null));
    }

    @Override
    public ChatErasure erase(String accountId, String anonymousSubject) {
        long readCursors =
                remove("chat_read_cursors", Criteria.where("accountId").is(accountId));
        long blocks = remove(
                "chat_blocks",
                new Criteria()
                        .orOperator(
                                Criteria.where("blockerId").is(accountId),
                                Criteria.where("blockedId").is(accountId)));
        long messages = remove("chat_messages", Criteria.where("senderId").is(accountId));
        long marketRooms = update(
                        "chat_rooms",
                        Criteria.where("buyerId").is(accountId),
                        new Update().set("buyerId", anonymousSubject))
                + update(
                        "chat_rooms",
                        Criteria.where("sellerId").is(accountId),
                        new Update().set("sellerId", anonymousSubject));
        long socialRooms = update(
                        "social_chat_rooms",
                        Criteria.where("memberIds").is(accountId),
                        new Update().pull("memberIds", accountId).unset("dedupeKey"))
                + update(
                        "social_chat_rooms",
                        Criteria.where("ownerId").is(accountId),
                        new Update().set("ownerId", anonymousSubject).unset("dedupeKey"));
        long reportSnapshots = update(
                        "chat_report_snapshots",
                        Criteria.where("reporterId").is(accountId),
                        new Update().set("reporterId", anonymousSubject))
                + update(
                        "chat_report_snapshots",
                        Criteria.where("messages.senderId").is(accountId),
                        new Update()
                                .set("messages.$[message].senderId", anonymousSubject)
                                .filterArray(Criteria.where("message.senderId").is(accountId)));
        return new ChatErasure(readCursors, blocks, messages, marketRooms, socialRooms, reportSnapshots);
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }

    private long update(String collection, Criteria criteria, Update update) {
        return mongo.updateMulti(Query.query(criteria), update, collection).getModifiedCount();
    }
}
