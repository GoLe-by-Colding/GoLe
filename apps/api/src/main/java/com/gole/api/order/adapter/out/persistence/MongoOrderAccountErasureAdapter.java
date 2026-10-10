package com.gole.api.order.adapter.out.persistence;

import com.gole.api.order.application.port.out.OrderAccountErasurePort;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 주문 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoOrderAccountErasureAdapter implements OrderAccountErasurePort {

    private static final List<String> ACTIVE_ORDER_STATUSES =
            List.of("PAYMENT_PENDING", "PAYMENT_REVIEW", "FUNDS_HELD", "DISPUTED", "REFUND_PENDING");

    private final MongoTemplate mongo;

    public MongoOrderAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean hasActiveOrder(String accountId) {
        Criteria party = new Criteria()
                .orOperator(
                        Criteria.where("buyerId").is(accountId),
                        Criteria.where("sellerId").is(accountId));
        return exists(
                "orders",
                new Criteria().andOperator(party, Criteria.where("status").in(ACTIVE_ORDER_STATUSES)));
    }

    @Override
    public boolean hasUnsettledPayout(String accountId) {
        return exists(
                "settlements",
                Criteria.where("sellerId").is(accountId).and("status").ne("PAID"));
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }
}
