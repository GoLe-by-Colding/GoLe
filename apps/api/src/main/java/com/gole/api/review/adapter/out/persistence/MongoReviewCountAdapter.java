package com.gole.api.review.adapter.out.persistence;

import com.gole.api.review.application.port.out.ReviewCountPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** 전체 후기 수를 컬렉션 메타데이터로 센다. 운영 대시보드 숫자라 정확한 집계 대신 추정치를 쓴다. */
@Component
public class MongoReviewCountAdapter implements ReviewCountPort {

    private final MongoTemplate mongoTemplate;

    public MongoReviewCountAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long estimatedReviewCount() {
        return mongoTemplate.estimatedCount(ReviewDocument.class);
    }
}
