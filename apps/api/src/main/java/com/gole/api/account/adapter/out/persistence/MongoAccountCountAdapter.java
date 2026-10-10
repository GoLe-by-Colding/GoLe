package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.AccountCountPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** 전체 계정 수를 컬렉션 메타데이터로 센다. 운영 대시보드 숫자라 정확한 집계 대신 추정치를 쓴다. */
@Component
public class MongoAccountCountAdapter implements AccountCountPort {

    private final MongoTemplate mongoTemplate;

    public MongoAccountCountAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long estimatedAccountCount() {
        return mongoTemplate.estimatedCount(AccountDocument.class);
    }
}
