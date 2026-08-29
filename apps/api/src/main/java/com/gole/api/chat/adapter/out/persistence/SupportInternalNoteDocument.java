package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "support_internal_notes")
@CompoundIndex(name = "support_note_room_created_idx", def = "{'roomId': 1, 'createdAt': -1}")
public class SupportInternalNoteDocument {

    @Id
    private String id;

    private String roomId;
    private String authorId;
    private String note;
    private Instant createdAt;

    protected SupportInternalNoteDocument() {}

    public SupportInternalNoteDocument(String id, String roomId, String authorId, String note, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.authorId = authorId;
        this.note = note;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
