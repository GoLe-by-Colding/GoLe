package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.adapter.out.persistence.AccountMongoRepository;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatReadCursorDocument;
import com.gole.api.chat.adapter.out.persistence.ChatReadCursorMongoRepository;
import com.gole.api.chat.adapter.out.persistence.SocialChatRoomMongoRepository;
import com.gole.api.chat.adapter.out.persistence.SupportConversationPurgeReceiptMongoRepository;
import com.gole.api.chat.adapter.out.persistence.SupportConversationRetentionHoldMongoRepository;
import com.gole.api.chat.adapter.out.persistence.SupportInternalNoteMongoRepository;
import com.gole.api.chat.adapter.out.persistence.SupportTicketMongoRepository;
import com.gole.api.chat.application.SupportConversationPrivacyService;
import com.gole.api.chat.application.SupportConversationPrivacyService.PurgeReasonCode;
import com.gole.api.chat.application.SupportConversationPrivacyService.RetentionHoldReasonCode;
import com.gole.api.chat.application.SupportConversationPrivacyService.RetentionReleaseReasonCode;
import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeWrite;
import com.gole.api.chat.application.port.out.SupportInternalNotePort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SupportConversationPurgeIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final String ADMIN_ID = "admin-purge";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        registry.add("gole.report.seed-on-empty", () -> "false");
        registry.add("gole.review.seed-on-empty", () -> "false");
        registry.add("gole.media.seed-on-startup", () -> "false");
        registry.add("gole.support-agent.enabled", () -> "false");
    }

    @Autowired
    SupportConversationPrivacyService service;

    @Autowired
    SupportConversationPrivacyRepositoryPort privacy;

    @Autowired
    SupportAssistantAnalysisRepositoryPort analyses;

    @Autowired
    AccountRepositoryPort accounts;

    @Autowired
    SupportTicketRepositoryPort tickets;

    @Autowired
    SocialChatRoomRepositoryPort rooms;

    @Autowired
    SupportInternalNotePort notes;

    @Autowired
    ChatReadStatePort readStates;

    @Autowired
    AccountMongoRepository accountDocuments;

    @Autowired
    SupportTicketMongoRepository ticketDocuments;

    @Autowired
    SocialChatRoomMongoRepository roomDocuments;

    @Autowired
    ChatMessageMongoRepository messageDocuments;

    @Autowired
    ChatReadCursorMongoRepository cursorDocuments;

    @Autowired
    SupportInternalNoteMongoRepository noteDocuments;

    @Autowired
    SupportConversationRetentionHoldMongoRepository holdDocuments;

    @Autowired
    SupportConversationPurgeReceiptMongoRepository receiptDocuments;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        messageDocuments.deleteAll();
        cursorDocuments.deleteAll();
        noteDocuments.deleteAll();
        ticketDocuments.deleteAll();
        roomDocuments.deleteAll();
        holdDocuments.deleteAll();
        receiptDocuments.deleteAll();
        accountDocuments.deleteAll();
        mongoTemplate.getDb().getCollection("support_assistant_analyses").deleteMany(new Document());
        mongoTemplate.getDb().getCollection("chat_report_snapshots").deleteMany(new Document());
        mongoTemplate.getDb().getCollection("orders").deleteMany(new Document());
        mongoTemplate.getDb().getCollection("admin_actions").deleteMany(new Document());
        accounts.save(Account.operationalBootstrap(
                ADMIN_ID, new Email("purge-admin@gole.test"), new PasswordHash("hash"), Role.ADMIN));
    }

    @Test
    void oneExplicitOperationPurgesLinkedRecordsAndCannotBeRecreatedOrReplayedDifferently() {
        String roomId = "support-purge-success";
        SupportTicket ticket = seedResolvedConversation(roomId);
        service.placeRetentionHold(roomId, ADMIN_ID, roomId, RetentionHoldReasonCode.LEGAL_OBLIGATION);
        service.releaseRetentionHold(roomId, ADMIN_ID, roomId, RetentionReleaseReasonCode.LEGAL_RELEASE_APPROVED);

        var first = service.purge(
                roomId,
                ADMIN_ID,
                roomId,
                PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED,
                true,
                "550e8400-e29b-41d4-a716-446655440001");
        var replay = service.purge(
                roomId,
                ADMIN_ID,
                roomId,
                PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED,
                true,
                "550e8400-e29b-41d4-a716-446655440001");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.receipt()).isEqualTo(first.receipt());
        assertThat(first.receipt().resolvedAt()).isEqualTo(ticket.resolvedAt());
        assertThat(first.receipt().counts().messages()).isEqualTo(2);
        assertThat(first.receipt().counts().supportTickets()).isEqualTo(1);
        assertThat(first.receipt().counts().socialRooms()).isEqualTo(1);
        assertThat(first.receipt().counts().assistantAnalyses()).isEqualTo(1);
        assertThat(first.receipt().counts().internalNotes()).isEqualTo(1);
        assertThat(first.receipt().counts().readCursors()).isEqualTo(1);
        assertThat(first.receipt().counts().retentionHolds()).isEqualTo(1);
        assertThat(first.receipt().counts().auditReferencesAnonymized()).isEqualTo(1);

        assertThat(ticketDocuments.findById(roomId)).isEmpty();
        assertThat(roomDocuments.findById(roomId)).isEmpty();
        assertThat(messageDocuments.findTop60ByRoomIdOrderBySentAtDesc(roomId)).isEmpty();
        assertThat(noteDocuments.count()).isZero();
        assertThat(cursorDocuments.count()).isZero();
        assertThat(holdDocuments.findById(roomId)).isEmpty();
        assertThat(mongoTemplate
                        .getDb()
                        .getCollection("support_assistant_analyses")
                        .find(new Document("_id", roomId))
                        .first())
                .isNull();
        assertThat(analyses.enqueue(roomId, NOW.plusSeconds(1))).isFalse();
        assertThat(mongoTemplate
                        .getDb()
                        .getCollection("support_assistant_analyses")
                        .find(new Document("_id", roomId))
                        .first())
                .isNull();
        assertThatThrownBy(() -> notes.append(roomId, ADMIN_ID, "파기 뒤 메모", NOW.plusSeconds(1)))
                .hasMessageContaining("이미 파기된");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                    privacy.fenceSupportConversation(roomId, NOW.plusSeconds(1));
                    readStates.advance(
                            roomId, "requester-purge", "message-b-" + roomId, NOW.minusSeconds(70), NOW.plusSeconds(1));
                }))
                .hasMessageContaining("이미 파기된");
        assertThat(noteDocuments.count()).isZero();
        assertThat(cursorDocuments.count()).isZero();

        Document receipt = mongoTemplate
                .getDb()
                .getCollection("support_conversation_purge_receipts")
                .find(new Document("_id", first.receipt().receiptId()))
                .first();
        assertThat(receipt).isNotNull();
        assertThat(receipt.keySet())
                .doesNotContain("roomId", "requesterId", "content", "summary", "draft", "freeformReason", "actorEmail");
        assertThat(receipt.toJson()).doesNotContain(roomId, "requester-purge", "민감");
        assertThat(receipt.getString("reasonCode")).isEqualTo("DATA_SUBJECT_REQUEST_FULFILLED");
        assertThat(receiptDocuments.count()).isEqualTo(1);

        Document anonymizedAudit = mongoTemplate
                .getDb()
                .getCollection("admin_actions")
                .find(new Document("_id", "audit-1"))
                .first();
        assertThat(anonymizedAudit).isNotNull();
        assertThat(anonymizedAudit.getString("targetId"))
                .isEqualTo(first.receipt().receiptId());
        assertThat(anonymizedAudit.toJson()).doesNotContain(roomId);

        assertThatThrownBy(() -> service.purge(
                        roomId,
                        ADMIN_ID,
                        roomId,
                        PurgeReasonCode.RETENTION_PERIOD_EXPIRED,
                        true,
                        "550e8400-e29b-41d4-a716-446655440001"))
                .hasMessageContaining("멱등 키");
    }

    @Test
    void mongoTransactionRollsBackEveryCollectionWhenOperationFailsAfterDeletes() {
        String roomId = "support-purge-rollback";
        seedResolvedConversation(roomId);
        SupportTicket ticket = tickets.findByRoomId(roomId).orElseThrow();
        PurgeWrite write = new PurgeWrite(
                UUID.randomUUID().toString(),
                roomId,
                ticket.version(),
                ticket.resolvedAt(),
                ADMIN_ID,
                PurgeReasonCode.DUPLICATE_OR_TEST_CONVERSATION.name(),
                sha256("integration-purge-key-rollback"),
                sha256(roomId + "\nrollback"),
                NOW);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                    privacy.purge(write);
                    throw new IllegalStateException("force rollback after receipt insert");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ticketDocuments.findById(roomId)).isPresent();
        assertThat(roomDocuments.findById(roomId)).isPresent();
        assertThat(messageDocuments.findTop60ByRoomIdOrderBySentAtDesc(roomId)).hasSize(2);
        assertThat(noteDocuments.count()).isEqualTo(1);
        assertThat(cursorDocuments.count()).isEqualTo(1);
        assertThat(mongoTemplate
                        .getDb()
                        .getCollection("support_assistant_analyses")
                        .find(new Document("_id", roomId))
                        .first())
                .isNotNull();
        assertThat(receiptDocuments.count()).isZero();
        Document originalAudit = mongoTemplate
                .getDb()
                .getCollection("admin_actions")
                .find(new Document("_id", "audit-1"))
                .first();
        assertThat(originalAudit.getString("targetId")).isEqualTo(roomId);
    }

    private SupportTicket seedResolvedConversation(String roomId) {
        SocialChatRoom room = SocialChatRoom.support(roomId, "requester-purge", "민감한 문의 제목", NOW.minusSeconds(90))
                .withSupportAgent(null, ADMIN_ID);
        rooms.save(room);
        SupportTicket ticket = tickets.save(SupportTicket.opened(roomId, "requester-purge", NOW.minusSeconds(90))
                .assignTo(ADMIN_ID, NOW.minusSeconds(60))
                .resolve(NOW.minusSeconds(30)));
        messageDocuments.saveAll(List.of(
                new ChatMessageDocument(
                        "message-a-" + roomId, roomId, "requester-purge", "민감한 문의 원문", NOW.minusSeconds(80)),
                new ChatMessageDocument("message-b-" + roomId, roomId, ADMIN_ID, "운영자 답변", NOW.minusSeconds(70))));
        notes.append(roomId, ADMIN_ID, "민감한 내부 메모", NOW.minusSeconds(60));
        cursorDocuments.save(new ChatReadCursorDocument(
                "cursor-" + roomId,
                roomId,
                "requester-purge",
                "message-b-" + roomId,
                NOW.minusSeconds(70),
                NOW.minusSeconds(60)));
        mongoTemplate
                .getDb()
                .getCollection("support_assistant_analyses")
                .insertOne(new Document("_id", roomId)
                        .append("state", "COMPLETED")
                        .append("summary", "민감한 AI 요약")
                        .append("draft", "민감한 답변 초안"));
        mongoTemplate
                .getDb()
                .getCollection("admin_actions")
                .insertOne(new Document("_id", "audit-1")
                        .append("actorId", ADMIN_ID)
                        .append("type", "SUPPORT_RESOLVE")
                        .append("targetType", "SUPPORT_TICKET")
                        .append("targetId", roomId)
                        .append("occurredAt", NOW.minusSeconds(30)));
        return ticket;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
