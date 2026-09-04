package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** 파기보다 우선하는 문의 대화 보존 중지 상태. 사유는 정형 코드로만 저장한다. */
@Document(collection = "support_conversation_retention_holds")
public class SupportConversationRetentionHoldDocument {

    @Id
    private String roomId;

    private String holdReference;
    private boolean active;
    private String reasonCode;
    private String placedBy;
    private Instant placedAt;
    private String releasedBy;
    private Instant releasedAt;
    private String releaseReasonCode;

    @Version
    private long version;

    protected SupportConversationRetentionHoldDocument() {}

    public SupportConversationRetentionHoldDocument(
            String roomId,
            String holdReference,
            boolean active,
            String reasonCode,
            String placedBy,
            Instant placedAt,
            String releasedBy,
            Instant releasedAt,
            String releaseReasonCode,
            long version) {
        this.roomId = roomId;
        this.holdReference = holdReference;
        this.active = active;
        this.reasonCode = reasonCode;
        this.placedBy = placedBy;
        this.placedAt = placedAt;
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
        this.releaseReasonCode = releaseReasonCode;
        this.version = version;
    }

    public String getRoomId() {
        return roomId;
    }

    public boolean isActive() {
        return active;
    }

    public String getHoldReference() {
        return holdReference;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getPlacedBy() {
        return placedBy;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleaseReasonCode() {
        return releaseReasonCode;
    }

    public long getVersion() {
        return version;
    }
}
