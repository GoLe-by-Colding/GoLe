package com.gole.api.pricing.adapter.out.persistence;

import com.gole.api.pricing.application.port.out.PriceTransactionCountPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** 전체 체결 기록 수를 컬렉션 메타데이터로 센다. 운영 대시보드 숫자라 정확한 집계 대신 추정치를 쓴다. */
@Component
public class MongoPriceTransactionCountAdapter implements PriceTransactionCountPort {

    private final MongoTemplate mongoTemplate;

    public MongoPriceTransactionCountAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long estimatedPriceTransactionCount() {
        return mongoTemplate.estimatedCount(PriceTransactionDocument.class);
    }
}
