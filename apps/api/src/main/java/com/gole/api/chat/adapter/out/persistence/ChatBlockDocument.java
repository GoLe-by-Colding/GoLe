package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_blocks")
@CompoundIndex(name = "block_pair_idx", def = "{'blockerId': 1, 'blockedId': 1}", unique = true)
public class ChatBlockDocument {

    @Id
    private String id;

    private String blockerId;
    private String blockedId;
    private String reason;
    private Instant blockedAt;

    protected ChatBlockDocument() {}

    public ChatBlockDocument(String id, String blockerId, String blockedId, String reason, Instant blockedAt) {
        this.id = id;
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.reason = reason;
        this.blockedAt = blockedAt;
    }

    public String getBlockerId() {
        return blockerId;
    }

    public String getBlockedId() {
        return blockedId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }
}
