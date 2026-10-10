package com.gole.api.listing.adapter.out.persistence;

import com.gole.api.listing.application.port.in.ListingAccountErasureUseCase.ListingErasure;
import com.gole.api.listing.application.port.out.ListingAccountErasurePort;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 매물 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoListingAccountErasureAdapter implements ListingAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoListingAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean hasPublicContent(String accountId) {
        return exists(
                        "listings",
                        Criteria.where("sellerId").is(accountId).and("status").in("ACTIVE", "RESERVED"))
                || exists(
                        "listing_comments",
                        Criteria.where("authorId").is(accountId).and("deleted").is(false));
    }

    @Override
    public ListingErasure erase(String accountId, String anonymousSubject) {
        long retired = update(
                "listings",
                Criteria.where("sellerId").is(accountId).and("status").in("SOLD", "DELETED"),
                new Update()
                        .set("sellerId", anonymousSubject)
                        .set("title", "탈퇴한 사용자의 매물")
                        .set("description", "")
                        .set("photoUrls", List.of()));
        long comments = update(
                "listing_comments",
                Criteria.where("authorId").is(accountId).and("deleted").is(true),
                new Update().set("authorId", anonymousSubject).set("content", ""));
        return new ListingErasure(retired, comments);
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private long update(String collection, Criteria criteria, Update update) {
        return mongo.updateMulti(Query.query(criteria), update, collection).getModifiedCount();
    }
}
