package com.gole.api.admin.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 관리자 조치 감사 로그 영속 모델. 도메인 {@code AdminAction}과 분리되며
 * 매핑은 {@link AdminAuditPersistenceAdapter}가 담당한다.
 */
@Document(collection = "admin_actions")
public class AdminActionDocument {

    @Id
    private String id;

    private String actorId;
    private String actorEmail;
    private String type;
    private String targetType;
    private String targetId;
    private String reason;

    @Indexed
    private Instant occurredAt;

    protected AdminActionDocument() {
        // MongoDB 매핑용
    }

    public AdminActionDocument(
            String id,
            String actorId,
            String actorEmail,
            String type,
            String targetType,
            String targetId,
            String reason,
            Instant occurredAt) {
        this.id = id;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getType() {
        return type;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
