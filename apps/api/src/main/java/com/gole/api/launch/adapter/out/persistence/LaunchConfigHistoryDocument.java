package com.gole.api.launch.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 공개 설정 변경 이력 영속 모델. 추가만 한다. */
@Document(collection = "launch_config_changes")
public class LaunchConfigHistoryDocument {

    @Id
    private String id;

    private String type;
    private String target;
    private String before;
    private String after;
    private String reason;
    private String actorId;
    private String actorEmail;

    @Indexed
    private Instant occurredAt;

    protected LaunchConfigHistoryDocument() {
        // MongoDB 매핑용
    }

    public LaunchConfigHistoryDocument(
            String id,
            String type,
            String target,
            String before,
            String after,
            String reason,
            String actorId,
            String actorEmail,
            Instant occurredAt) {
        this.id = id;
        this.type = type;
        this.target = target;
        this.before = before;
        this.after = after;
        this.reason = reason;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public String getBefore() {
        return before;
    }

    public String getAfter() {
        return after;
    }

    public String getReason() {
        return reason;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
