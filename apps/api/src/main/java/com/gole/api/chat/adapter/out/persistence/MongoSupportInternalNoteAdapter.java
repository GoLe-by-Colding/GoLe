package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportInternalNotePort;
import com.gole.api.common.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoSupportInternalNoteAdapter implements SupportInternalNotePort {

    private final SupportInternalNoteMongoRepository notes;
    private final MongoTemplate mongoTemplate;

    public MongoSupportInternalNoteAdapter(SupportInternalNoteMongoRepository notes, MongoTemplate mongoTemplate) {
        this.notes = notes;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void append(String roomId, String authorId, String note, Instant at) {
        // addNote 유스케이스의 트랜잭션 안에서 파기와 같은 티켓 문서에 쓰기 펜스를 건다.
        // 파기가 먼저 끝났거나 동시에 경합하면 고아 메모를 만들지 않는다.
        var fence = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(roomId)),
                new Update().set("internalNoteFenceAt", at),
                SupportTicketDocument.class);
        if (fence.getMatchedCount() == 0) {
            throw new ConflictException("SUPPORT_CONVERSATION_ALREADY_PURGED", "이미 파기된 문의 대화입니다");
        }
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
