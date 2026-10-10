package com.gole.api.bid.adapter.out.persistence;

import com.gole.api.bid.application.port.in.BidAccountErasureUseCase.BidErasure;
import com.gole.api.bid.application.port.out.BidAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 구매 입찰 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoBidAccountErasureAdapter implements BidAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoBidAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public BidErasure erase(String accountId, String anonymousSubject) {
        // 입찰은 입찰자 개인의 구매 의사라 익명화하지 않고 지운다. 체결로 생긴 제안은 offer 쪽 정리가 맡는다. (buy-bids D11)
        return new BidErasure(remove("bids", Criteria.where("bidderId").is(accountId)));
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }
}
