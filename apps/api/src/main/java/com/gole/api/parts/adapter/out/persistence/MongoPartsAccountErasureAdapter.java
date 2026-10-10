package com.gole.api.parts.adapter.out.persistence;

import com.gole.api.parts.application.port.in.PartsAccountErasureUseCase.PartsErasure;
import com.gole.api.parts.application.port.out.PartsAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 부품 요청 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoPartsAccountErasureAdapter implements PartsAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoPartsAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public PartsErasure erase(String accountId, String anonymousSubject) {
        // 부품 요청은 작성자 개인의 게시물이라 익명화하지 않고 지운다. (wanted-parts W11)
        return new PartsErasure(
                remove("part_requests", Criteria.where("requesterId").is(accountId)));
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }
}
