package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportInternalNotePort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MongoSupportInternalNoteAdapter implements SupportInternalNotePort {

    private final SupportInternalNoteMongoRepository notes;

    public MongoSupportInternalNoteAdapter(SupportInternalNoteMongoRepository notes) {
        this.notes = notes;
    }

    @Override
    public void append(String roomId, String authorId, String note, Instant at) {
        notes.save(new SupportInternalNoteDocument(UUID.randomUUID().toString(), roomId, authorId, note, at));
    }

    @Override
    public List<InternalNote> findByRoom(String roomId, int limit) {
        var page = PageRequest.of(0, Math.clamp(limit, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return notes.findByRoomId(roomId, page).stream()
                .map(row -> new InternalNote(
                        row.getId(), row.getRoomId(), row.getAuthorId(), row.getNote(), row.getCreatedAt()))
                .toList();
    }
}
