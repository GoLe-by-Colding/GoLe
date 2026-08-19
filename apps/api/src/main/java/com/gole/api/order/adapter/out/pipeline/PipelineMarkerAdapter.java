package com.gole.api.order.adapter.out.pipeline;

import com.gole.api.order.application.port.out.PipelineMarkerPort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Mongo 유니크 인덱스 기반 마커 어댑터. insert 충돌(DuplicateKey)이 곧 "이미 처리됨"이다 —
 * 조회 후 삽입 사이의 경쟁을 원자적으로 막는다.
 */
@Component
public class PipelineMarkerAdapter implements PipelineMarkerPort {

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public PipelineMarkerAdapter(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    @Override
    public boolean markOnce(String rule, String refId) {
        try {
            mongoTemplate.insert(
                    new PipelineMarkerDocument(UUID.randomUUID().toString(), rule, refId, Instant.now(clock)));
            return true;
        } catch (DuplicateKeyException alreadyMarked) {
            return false;
        }
    }
}
