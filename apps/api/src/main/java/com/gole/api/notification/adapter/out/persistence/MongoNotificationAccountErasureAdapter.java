package com.gole.api.notification.adapter.out.persistence;

import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase.NotificationErasure;
import com.gole.api.notification.application.port.out.NotificationAccountErasurePort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 알림 기록의 차단 판정·파기. 규칙은 계정 탈퇴 어댑터에 있던 것을 그대로 옮겼다
 * (account-deletion-participants D1). 호출자의 Mongo 트랜잭션에 함께 묶인다.
 */
@Component
public class MongoNotificationAccountErasureAdapter implements NotificationAccountErasurePort {

    private final MongoTemplate mongo;

    public MongoNotificationAccountErasureAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public NotificationErasure erase(String accountId, String anonymousSubject) {
        return new NotificationErasure(
                remove("notifications", Criteria.where("recipientId").is(accountId)),
                remove("notification_preferences", Criteria.where("_id").is(accountId)));
    }

    private long remove(String collection, Criteria criteria) {
        return mongo.remove(Query.query(criteria), collection).getDeletedCount();
    }
}
