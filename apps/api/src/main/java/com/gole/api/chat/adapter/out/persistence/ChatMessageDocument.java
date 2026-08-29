package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_messages")
@CompoundIndexes({
    @CompoundIndex(name = "room_sent_at_idx", def = "{'roomId': 1, 'sentAt': -1}"),
    @CompoundIndex(name = "room_sent_at_id_idx", def = "{'roomId': 1, 'sentAt': -1, '_id': -1}")
})
public class ChatMessageDocument {

    @Id
    private String id;

    private String roomId;

    private String senderId;
    private String content;
    private Instant sentAt;

    protected ChatMessageDocument() {}

    public ChatMessageDocument(String id, String roomId, String senderId, String content, Instant sentAt) {
        this.id = id;
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
