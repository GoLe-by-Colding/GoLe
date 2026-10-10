package com.gole.api.review.adapter.out.persistence;

import com.gole.api.review.application.port.in.ReviewAccountErasureUseCase.ReviewErasure;
import com.gole.api.review.application.port.out.ReviewAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 후기 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoReviewAccountErasureAdapter implements ReviewAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoReviewAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public ReviewErasure erase(String accountId, String anonymousSubject) {
        return new ReviewErasure(update(
                        "reviews",
                        Criteria.where("reviewerId").is(accountId),
                        new Update()
                                .set("reviewerId", anonymousSubject)
                                .set("content", "")
                                .unset("reply"))
                + update(
                        "reviews",
                        Criteria.where("revieweeId").is(accountId),
                        new Update().set("revieweeId", anonymousSubject).unset("reply")));
    }

    private long update(String collection, Criteria criteria, Update update) {
        return mongo.updateMulti(Query.query(criteria), update, collection).getModifiedCount();
    }
}
