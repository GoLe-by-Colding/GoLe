package com.gole.api.collection.adapter.out.persistence;

import com.gole.api.collection.application.port.in.CollectionAccountErasureUseCase.CollectionErasure;
import com.gole.api.collection.application.port.out.CollectionAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 컬렉션 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoCollectionAccountErasureAdapter implements CollectionAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoCollectionAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public CollectionErasure erase(String accountId, String anonymousSubject) {
        return new CollectionErasure(
                remove("collection_items", Criteria.where("userId").is(accountId)),
                remove("collection_value_snapshots", Criteria.where("userId").is(accountId)));
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }
}
