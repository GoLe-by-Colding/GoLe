package com.gole.api.offer.adapter.out.persistence;

import com.gole.api.offer.application.port.in.OfferAccountErasureUseCase.OfferErasure;
import com.gole.api.offer.application.port.out.OfferAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 가격 제안 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoOfferAccountErasureAdapter implements OfferAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoOfferAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public OfferErasure erase(String accountId, String anonymousSubject) {
        // 가격 제안은 두 당사자 사이의 협상 기록이다. 어느 쪽이 탈퇴해도 상대에게 의미가 없으므로 지운다.
        // 주문에 남은 offerId는 금액 근거 표시용이라 끊어져도 주문 처리에 영향이 없다. (price-offer O22)
        return new OfferErasure(remove(
                "offers",
                new Criteria()
                        .orOperator(
                                Criteria.where("buyerId").is(accountId),
                                Criteria.where("sellerId").is(accountId))));
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }
}
