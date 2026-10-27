package com.gole.api.shipping.adapter.out.persistence;

import com.gole.api.shipping.application.port.out.TrackerAdminAuditPort;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrackerAdminAuditAdapter implements TrackerAdminAuditPort {
    private final MongoTemplate mongo;
    private final Clock clock;

    public TrackerAdminAuditAdapter(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    public void record(String actorId, String action, String targetId) {
        // 감사 기록을 영구 저장하지 못하면 외부 제공자를 호출하지 않는다.
        mongo.insert(new Entry(actorId, action, targetId, clock.instant()), "shipping_tracker_admin_actions");
    }

    record Entry(String actorId, String action, String targetId, Instant occurredAt) {}
}
