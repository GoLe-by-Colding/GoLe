package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_read_cursors")
@CompoundIndexes({
    @CompoundIndex(name = "room_account_unique_idx", def = "{'roomId': 1, 'accountId': 1}", unique = true),
    @CompoundIndex(name = "account_room_idx", def = "{'accountId': 1, 'roomId': 1}")
})
public class ChatReadCursorDocument {

    @Id
    private String id;

    private String roomId;
    private String accountId;
    private String lastReadMessageId;
    private Instant lastReadSentAt;
    private Instant updatedAt;

    protected ChatReadCursorDocument() {}

    public ChatReadCursorDocument(
            String id,
            String roomId,
            String accountId,
            String lastReadMessageId,
            Instant lastReadSentAt,
            Instant updatedAt) {
        this.id = id;
        this.roomId = roomId;
        this.accountId = accountId;
        this.lastReadMessageId = lastReadMessageId;
        this.lastReadSentAt = lastReadSentAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getLastReadMessageId() {
        return lastReadMessageId;
    }

    public Instant getLastReadSentAt() {
        return lastReadSentAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
