package com.gole.api.discovery.adapter.out.persistence;

import com.gole.api.discovery.application.port.in.DiscoveryAccountErasureUseCase.DiscoveryErasure;
import com.gole.api.discovery.application.port.out.DiscoveryAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 찜·팔로우 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoDiscoveryAccountErasureAdapter implements DiscoveryAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoDiscoveryAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public DiscoveryErasure erase(String accountId, String anonymousSubject) {
        return new DiscoveryErasure(
                remove("wishlist_entries", Criteria.where("userId").is(accountId)),
                remove(
                        "follows",
                        new Criteria()
                                .orOperator(
                                        Criteria.where("userId").is(accountId),
                                        Criteria.where("sellerId").is(accountId))));
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }
}
