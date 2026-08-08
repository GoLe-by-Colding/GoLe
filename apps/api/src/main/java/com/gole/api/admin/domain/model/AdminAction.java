package com.gole.api.admin.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 관리자 조치 감사 레코드. (admin-console 요구사항 8)
 *
 * <p>append-only 불변 객체다. 한 번 기록된 조치는 수정/삭제하지 않으므로 setter가 없다.
 * 조치자 이메일은 조치 시점의 <b>스냅샷</b>으로 함께 보관한다 — 계정이 삭제되거나 이메일이
 * 바뀌어도 "누가 했는지"를 추적할 수 있어야 하기 때문이다.
 */
public final class AdminAction {

    private final String id;
    private final String actorId;
    private final String actorEmail;
    private final AdminActionType type;
    private final AdminTargetType targetType;
    private final String targetId;
    private final String reason; // nullable
    private final Instant occurredAt;

    public AdminAction(
            String id,
            String actorId,
            String actorEmail,
            AdminActionType type,
            AdminTargetType targetType,
            String targetId,
            String reason,
            Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorEmail = actorEmail == null ? "" : actorEmail;
        this.type = Objects.requireNonNull(type, "type");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.reason = reason;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
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

    public AdminActionType getType() {
        return type;
    }

    public AdminTargetType getTargetType() {
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
