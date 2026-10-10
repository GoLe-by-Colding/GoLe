package com.gole.api.media.adapter.out.persistence;

import com.gole.api.media.application.port.in.MediaAccountErasureUseCase.MediaErasure;
import com.gole.api.media.application.port.out.MediaAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 미디어 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoMediaAccountErasureAdapter implements MediaAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoMediaAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean hasLiveMedia(String accountId) {
        return exists(
                "media_assets",
                Criteria.where("ownerId").is(accountId).and("status").ne("REVOKED"));
    }

    @Override
    public MediaErasure erase(String accountId, String anonymousSubject) {
        return new MediaErasure(update(
                "media_assets",
                Criteria.where("ownerId").is(accountId).and("status").is("REVOKED"),
                new Update().set("ownerId", anonymousSubject)));
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private long update(String collection, Criteria criteria, Update update) {
        return mongo.updateMulti(Query.query(criteria), update, collection).getModifiedCount();
    }
}
