package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.admin.adapter.out.persistence.AdminActionDocument;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.common.exception.ConflictException;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoSupportConversationPrivacyAdapter implements SupportConversationPrivacyRepositoryPort {

    private final SupportConversationRetentionHoldMongoRepository holds;
    private final SupportConversationPurgeReceiptMongoRepository receipts;
    private final MongoTemplate mongoTemplate;

    public MongoSupportConversationPrivacyAdapter(
            SupportConversationRetentionHoldMongoRepository holds,
            SupportConversationPurgeReceiptMongoRepository receipts,
            MongoTemplate mongoTemplate) {
        this.holds = holds;
        this.receipts = receipts;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<RetentionHold> findRetentionHold(String roomId) {
        return holds.findById(roomId).map(MongoSupportConversationPrivacyAdapter::toHold);
    }

    @Override
    public RetentionHold saveRetentionHold(RetentionHold hold) {
        var changedAt = hold.releasedAt() == null ? hold.placedAt() : hold.releasedAt();
        var fence = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(hold.roomId())),
                new Update().set("retentionHoldFenceAt", changedAt),
                SupportTicketDocument.class);
        if (fence.getMatchedCount() == 0) {
            throw new ConflictException("SUPPORT_CONVERSATION_ALREADY_PURGED", "이미 파기된 문의 대화입니다");
        }
        try {
            return toHold(holds.save(toDocument(hold)));
        } catch (DuplicateKeyException | OptimisticLockingFailureException concurrentChange) {
            throw new ConflictException("SUPPORT_RETENTION_HOLD_CONCURRENT_UPDATE", "보존 중지 상태가 다른 관리자에 의해 변경되었습니다");
        }
    }

    @Override
    public Optional<PurgeReceipt> findPurgeReceiptByIdempotencyKeyHash(String idempotencyKeyHash) {
        return receipts.findByIdempotencyKeyHash(idempotencyKeyHash)
                .map(MongoSupportConversationPrivacyAdapter::toReceipt);
    }

    @Override
    public void fenceSupportConversation(String roomId, java.time.Instant changedAt) {
        var fence = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(roomId)),
                new Update().set("readStateFenceAt", changedAt),
                SupportTicketDocument.class);
        if (fence.getMatchedCount() == 0) {
            throw new ConflictException("SUPPORT_CONVERSATION_ALREADY_PURGED", "이미 파기된 문의 대화입니다");
        }
    }

    @Override
    public PurgeReceipt purge(PurgeWrite command) {
        Query resolvedTicket = Query.query(new Criteria()
                .andOperator(
                        Criteria.where("_id").is(command.roomId()),
                        Criteria.where("status").is(SupportStatus.RESOLVED.name()),
                        Criteria.where("version").is(command.expectedTicketVersion())));
        long deletedTickets = mongoTemplate
                .remove(resolvedTicket, SupportTicketDocument.class)
                .getDeletedCount();
        if (deletedTickets != 1) {
            throw new ConflictException("SUPPORT_PURGE_STALE_TICKET", "문의 상태가 변경되었습니다. 다시 확인해 주세요");
        }

        long deletedRooms = mongoTemplate
                .remove(
                        Query.query(new Criteria()
                                .andOperator(
                                        Criteria.where("_id").is(command.roomId()),
                                        Criteria.where("type").is(ChatRoomType.SUPPORT.name()))),
                        SocialChatRoomDocument.class)
                .getDeletedCount();
        if (deletedRooms != 1) {
            throw new ConflictException("SUPPORT_PURGE_STALE_ROOM", "문의 대화방 상태가 변경되었습니다. 다시 확인해 주세요");
        }

        long deletedMessages = removeByRoomId(ChatMessageDocument.class, command.roomId());
        long deletedAnalyses = mongoTemplate
                .remove(Query.query(Criteria.where("_id").is(command.roomId())), SupportAssistantAnalysisDocument.class)
                .getDeletedCount();
        long deletedNotes = removeByRoomId(SupportInternalNoteDocument.class, command.roomId());
        long deletedCursors = removeByRoomId(ChatReadCursorDocument.class, command.roomId());
        long deletedHolds = mongoTemplate
                .remove(
                        Query.query(new Criteria()
                                .andOperator(
                                        Criteria.where("_id").is(command.roomId()),
                                        Criteria.where("active").is(false))),
                        SupportConversationRetentionHoldDocument.class)
                .getDeletedCount();
        long anonymizedAuditReferences = mongoTemplate
                .updateMulti(
                        Query.query(new Criteria()
                                .andOperator(
                                        Criteria.where("targetType").is(AdminTargetType.SUPPORT_TICKET.name()),
                                        Criteria.where("targetId").is(command.roomId()))),
                        new Update().set("targetId", command.receiptId()),
                        AdminActionDocument.class)
                .getModifiedCount();

        PurgeCounts counts = new PurgeCounts(
                deletedMessages,
                deletedTickets,
                deletedRooms,
                deletedAnalyses,
                deletedNotes,
                deletedCursors,
                deletedHolds,
                anonymizedAuditReferences);
        SupportConversationPurgeReceiptDocument receipt = new SupportConversationPurgeReceiptDocument(
                command.receiptId(),
                command.actorId(),
                command.reasonCode(),
                command.idempotencyKeyHash(),
                command.requestFingerprint(),
                command.resolvedAt(),
                command.purgedAt(),
                counts.messages(),
                counts.supportTickets(),
                counts.socialRooms(),
                counts.assistantAnalyses(),
                counts.internalNotes(),
                counts.readCursors(),
                counts.retentionHolds(),
                counts.auditReferencesAnonymized());
        try {
            return toReceipt(mongoTemplate.insert(receipt));
        } catch (DuplicateKeyException duplicateReceipt) {
            throw new ConflictException("SUPPORT_PURGE_IDEMPOTENCY_CONFLICT", "같은 문의 또는 멱등 키의 파기 기록이 이미 존재합니다");
        }
    }

    private long removeByRoomId(Class<?> documentType, String roomId) {
        return mongoTemplate
                .remove(Query.query(Criteria.where("roomId").is(roomId)), documentType)
                .getDeletedCount();
    }

    private static SupportConversationRetentionHoldDocument toDocument(RetentionHold hold) {
        return new SupportConversationRetentionHoldDocument(
                hold.roomId(),
                hold.holdReference(),
                hold.active(),
                hold.reasonCode(),
                hold.placedBy(),
                hold.placedAt(),
                hold.releasedBy(),
                hold.releasedAt(),
                hold.releaseReasonCode(),
                hold.version());
    }

    private static RetentionHold toHold(SupportConversationRetentionHoldDocument document) {
        return new RetentionHold(
                document.getRoomId(),
                document.getHoldReference(),
                document.isActive(),
                document.getReasonCode(),
                document.getPlacedBy(),
                document.getPlacedAt(),
                document.getReleasedBy(),
                document.getReleasedAt(),
                document.getReleaseReasonCode(),
                document.getVersion());
    }

    private static PurgeReceipt toReceipt(SupportConversationPurgeReceiptDocument document) {
        return new PurgeReceipt(
                document.getReceiptId(),
                document.getActorId(),
                document.getReasonCode(),
                document.getIdempotencyKeyHash(),
                document.getRequestFingerprint(),
                document.getResolvedAt(),
                document.getPurgedAt(),
                new PurgeCounts(
                        document.getMessages(),
                        document.getSupportTickets(),
                        document.getSocialRooms(),
                        document.getAssistantAnalyses(),
                        document.getInternalNotes(),
                        document.getReadCursors(),
                        document.getRetentionHolds(),
                        document.getAuditReferencesAnonymized()));
    }
}
