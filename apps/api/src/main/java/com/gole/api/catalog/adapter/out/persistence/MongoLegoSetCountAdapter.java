package com.gole.api.catalog.adapter.out.persistence;

import com.gole.api.catalog.application.port.out.LegoSetCountPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** 전체 세트 수를 컬렉션 메타데이터로 센다. 운영 대시보드 숫자라 정확한 집계 대신 추정치를 쓴다. */
@Component
public class MongoLegoSetCountAdapter implements LegoSetCountPort {

    private final MongoTemplate mongoTemplate;

    public MongoLegoSetCountAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long estimatedLegoSetCount() {
        return mongoTemplate.estimatedCount(LegoSetDocument.class);
    }
}
