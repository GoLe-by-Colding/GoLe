package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.SupportTicketDocument;
import com.gole.api.chat.application.port.out.ChatMessagePublisherPort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.application.service.SupportChatService;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotice;
import com.gole.api.chat.domain.model.SupportNoticeFilter;
import com.gole.api.chat.domain.model.SupportNoticeKind;
import com.gole.api.chat.domain.model.SupportNoticeScope;
import com.gole.api.chat.domain.model.SupportNoticeSummary;
import com.gole.api.chat.domain.model.SupportOperator;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SupportNoticeOperationsIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final String ADMIN = "notice-admin";
    private static final SupportOperator OPERATOR = new SupportOperator(ADMIN, "notice-admin@gole.test");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        for (String seed : List.of("catalog", "listing", "pricing", "community", "report", "review")) {
            registry.add("gole." + seed + ".seed-on-empty", () -> "false");
        }
        registry.add("gole.media.seed-on-startup", () -> "false");
        registry.add("gole.support-agent.enabled", () -> "false");
    }

    @Autowired
    SupportChatService service;

    @MockitoSpyBean
    SupportTicketRepositoryPort tickets;

    @Autowired
    SocialChatRoomRepositoryPort rooms;

    @Autowired
    AccountRepositoryPort accounts;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    ChatMessagePublisherPort publisher;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        for (String collection :
                List.of("support_tickets", "social_chat_rooms", "chat_messages", "admin_actions", "accounts")) {
            mongo.getCollection(collection).deleteMany(new Document());
        }
        accounts.save(
                Account.operationalBootstrap(ADMIN, new Email(OPERATOR.email()), new PasswordHash("hash"), Role.ADMIN));
    }

    @Test
    @DisplayName("실제 답변·상태·안내 원장·감사가 저장된 뒤에만 실시간 메시지를 발행함")
    void reply_persistsMessageNoticeStateAndAuditInOneTransaction() {
        seedConversation("reply");

        ChatMessage message = service.replyWithNotice("reply", OPERATOR, "확인한 결과와 처리방안입니다.", SupportNoticeKind.RESULT);

        SupportTicket saved = tickets.findByRoomId("reply").orElseThrow();
        assertThat(saved.status()).isEqualTo(SupportStatus.WAITING_USER);
        assertThat(saved.notices())
                .containsExactly(new SupportNotice(SupportNoticeKind.RESULT, message.id(), ADMIN, NOW));
        assertThat(saved.firstNoticeAt(SupportNoticeKind.PROGRESS)).isEqualTo(NOW);
        assertThat(saved.firstNoticeAt(SupportNoticeKind.RESULT)).isEqualTo(NOW);
        assertThat(mongo.findById(message.id(), ChatMessageDocument.class)).isNotNull();
        Document audit = mongo.getCollection("admin_actions")
                .find(new Document("targetId", "reply"))
                .first();
        assertThat(audit).isNotNull();
        assertThat(audit.getString("reason")).contains("notice=RESULT", message.id());
        verify(publisher).publish(message);
    }

    @Test
    @DisplayName("트랜잭션 실패 시 답변·안내 원장·상태·감사가 함께 되돌아가고 발행하지 않음")
    void reply_rollsBackEveryRecordAndDoesNotPublishOnTransactionFailure() {
        SupportTicket original = seedConversation("rollback");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                    service.replyWithNotice("rollback", OPERATOR, "저장 후 실패할 안내", SupportNoticeKind.PROGRESS);
                    assertThat(tickets.findByRoomId("rollback").orElseThrow().notices())
                            .hasSize(1);
                    throw new IllegalStateException("안내 저장 뒤 강제 롤백");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(tickets.findByRoomId("rollback")).contains(original);
        assertThat(mongo.count(Query.query(Criteria.where("roomId").is("rollback")), ChatMessageDocument.class))
                .isZero();
        assertThat(mongo.getCollection("admin_actions").countDocuments(new Document("targetId", "rollback")))
                .isZero();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("일반 답변과 해결 처리는 안내 이행으로 집계하지 않음")
    void ordinaryReplyAndResolution_doNotInventNoticeEvidence() {
        seedConversation("general");
        service.reply("general", OPERATOR, "추가로 확인할 내용을 알려 주세요.");
        service.resolve("general", OPERATOR);

        SupportTicket saved = tickets.findByRoomId("general").orElseThrow();
        assertThat(saved.status()).isEqualTo(SupportStatus.RESOLVED);
        assertThat(saved.notices()).isEmpty();
        assertThat(tickets.noticeSummary(NOW).progress().overdue()).isEqualTo(1);
        assertThat(tickets.findByNotice(null, null, SupportNoticeFilter.PROGRESS_OVERDUE, NOW, 50))
                .extracting(SupportTicket::roomId)
                .containsExactly("general");
    }

    @ParameterizedTest
    @EnumSource(SupportNoticeKind.class)
    @DisplayName("안내별 기한 경계와 최초 안내를 전체 집계·큐가 동일하게 분류함")
    void summaryAndQueue_classifyExactBoundariesAndFirstNotice(SupportNoticeKind kind) {
        Instant cutoff = NOW.minus(kind.targetAfter());
        saveTicket("pending", cutoff.plus(kind.warningWindow()).plusMillis(1), List.of());
        saveTicket("warning", cutoff.plus(kind.warningWindow()), List.of());
        saveTicket("exact-due", cutoff, List.of());
        saveTicket("overdue", cutoff.minusMillis(1), List.of());
        saveTicket(
                "on-time",
                cutoff.minusSeconds(3600),
                List.of(
                        new SupportNotice(kind, "first", ADMIN, NOW.minusSeconds(3600)),
                        new SupportNotice(kind, "repeat", ADMIN, NOW)));
        saveTicket(
                "late",
                cutoff.minusSeconds(3600),
                List.of(new SupportNotice(kind, "late", ADMIN, NOW.minusSeconds(3599))));

        SupportNoticeSummary summary = tickets.noticeSummary(NOW);
        assertThat(summary.total()).isEqualTo(6);
        assertThat(summary.generatedAt()).isEqualTo(NOW);
        assertThat(kind == SupportNoticeKind.PROGRESS ? summary.progress() : summary.result())
                .isEqualTo(new SupportNoticeSummary.Counts(1, 2, 1, 1, 1));
        SupportNoticeFilter dueSoon = SupportNoticeFilter.valueOf(kind.name() + "_DUE_SOON");
        SupportNoticeFilter overdue = SupportNoticeFilter.valueOf(kind.name() + "_OVERDUE");
        assertThat(tickets.findByNotice(null, null, dueSoon, NOW, 50))
                .extracting(SupportTicket::roomId)
                .containsExactly("exact-due", "warning");
        assertThat(tickets.findByNotice(null, null, overdue, NOW, 50))
                .extracting(SupportTicket::roomId)
                .containsExactly("overdue");
        if (kind == SupportNoticeKind.RESULT) {
            assertThat(summary.progress().late()).isEqualTo(2);
        } else {
            assertThat(summary.result().onTime() + summary.result().late()).isZero();
        }
    }

    @Test
    @DisplayName("100건 뒤로 밀린 오래된 문의도 전체 집계와 필터 우선 조회에서 빠지지 않음")
    void noticeQueue_filtersWholeCollectionBeforeLimitAndIncludesLegacyResolvedTickets() {
        for (int i = 0; i < 120; i++) saveTicket("new-" + i, NOW.minusSeconds(i), List.of());
        Date old = Date.from(NOW.minusSeconds(11 * 86400));
        mongo.getCollection("support_tickets")
                .insertOne(new Document("_id", "legacy-resolved")
                        .append("requesterId", "requester")
                        .append("status", "RESOLVED")
                        .append("assigneeId", ADMIN)
                        .append("createdAt", old)
                        .append("updatedAt", old)
                        .append("resolvedAt", old)
                        .append("version", 1L));
        saveTicket("old-trade", NOW.minusSeconds(10 * 86400 + 1), List.of());
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("old-trade")),
                new org.springframework.data.mongodb.core.query.Update().set("category", "TRADE"),
                SupportTicketDocument.class);

        assertThat(tickets.findByStatus(null, 100))
                .extracting(SupportTicket::roomId)
                .doesNotContain("legacy-resolved");
        assertThat(tickets.noticeSummary(NOW).total()).isEqualTo(122);
        assertThat(tickets.noticeSummary(NOW).result().overdue()).isEqualTo(2);
        assertThat(tickets.findByNotice(null, null, SupportNoticeFilter.RESULT_OVERDUE, NOW, 1))
                .extracting(SupportTicket::roomId)
                .containsExactly("legacy-resolved");
        assertThat(tickets.findByNotice(
                        SupportStatus.RESOLVED, SupportCategory.GENERAL, SupportNoticeFilter.RESULT_OVERDUE, NOW, 50))
                .singleElement()
                .satisfies(ticket -> {
                    assertThat(ticket.roomId()).isEqualTo("legacy-resolved");
                    assertThat(ticket.notices()).isEmpty();
                    assertThat(ticket.category()).isEqualTo(SupportCategory.GENERAL);
                });
        assertThat(tickets.findByNotice(
                        SupportStatus.IN_PROGRESS, SupportCategory.TRADE, SupportNoticeFilter.RESULT_OVERDUE, NOW, 50))
                .extracting(SupportTicket::roomId)
                .containsExactly("old-trade");
    }

    @Test
    @DisplayName("보존 문의가 없으면 빈 집계를 반환함")
    void summary_returnsZeroOnlyForActuallyEmptyCollection() {
        SupportNoticeSummary.Counts zero = new SupportNoticeSummary.Counts(0, 0, 0, 0, 0);
        assertThat(tickets.noticeSummary(NOW))
                .isEqualTo(new SupportNoticeSummary(NOW, 0, zero, zero, zero, zero, 0, 0));
    }

    @Test
    @DisplayName("해결 미기록 100건이 미해결 초과 작업 큐를 가리지 않으며 별도로 확인됨")
    void activeQueue_isNotHiddenBehindResolvedMissingHistory() {
        for (int i = 0; i < 100; i++) {
            SupportTicket old = saveTicket("resolved-" + i, NOW.minusSeconds(20 * 86400 + i), List.of());
            tickets.save(old.resolve(NOW.minusSeconds(86400)));
        }
        saveTicket("action-required", NOW.minusSeconds(11 * 86400), List.of());
        var summary = tickets.noticeSummary(NOW);
        assertThat(summary.total()).isEqualTo(101);
        assertThat(summary.result().overdue()).isEqualTo(101);
        assertThat(summary.activeResult().overdue()).isEqualTo(1);
        assertThat(summary.resolvedMissingResult()).isEqualTo(100);
        assertThat(summary.resolvedMissingProgress()).isEqualTo(100);
        assertThat(tickets.findByNotice(
                        null, null, SupportNoticeFilter.RESULT_OVERDUE, SupportNoticeScope.ACTIVE, NOW, 50))
                .extracting(SupportTicket::roomId)
                .containsExactly("action-required");
        assertThat(tickets.findByNotice(
                        null, null, SupportNoticeFilter.RESULT_MISSING, SupportNoticeScope.RESOLVED, NOW, 50))
                .hasSize(50)
                .allMatch(ticket -> ticket.status() == SupportStatus.RESOLVED
                        && ticket.notices().isEmpty());
        assertThat(tickets.findByNotice(
                        SupportStatus.IN_PROGRESS,
                        null,
                        SupportNoticeFilter.RESULT_MISSING,
                        SupportNoticeScope.RESOLVED,
                        NOW,
                        50))
                .isEmpty();
    }

    @Test
    @DisplayName("담당자 인수와 안내 답변이 겹치면 답변을 충돌로 거부하고 부분 저장하지 않음")
    void concurrentTakeover_rollsBackReplyAsConflict() throws Exception {
        seedConversation("race");
        accounts.save(Account.operationalBootstrap(
                "next-admin", new Email("next-admin@gole.test"), new PasswordHash("hash"), Role.ADMIN));
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean pauseOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    if (Thread.currentThread().getName().equals("notice-race") && pauseOnce.getAndSet(false)) {
                        read.countDown();
                        if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 갱신 재현 시간 초과");
                    }
                    return result;
                })
                .when(tickets)
                .findByRoomId("race");
        try (var executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "notice-race"))) {
            var reply = executor.submit(() -> {
                try {
                    service.replyWithNotice("race", OPERATOR, "동시 조치 안내", SupportNoticeKind.RESULT);
                    return (Throwable) null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            try {
                assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
                service.takeOver("race", new SupportOperator("next-admin", "next-admin@gole.test"), "담당자 부재로 인수");
            } finally {
                resume.countDown();
            }
            assertThat(reply.get(10, TimeUnit.SECONDS)).isInstanceOf(ConflictException.class);
        }
        assertThat(tickets.findByRoomId("race")).get().satisfies(ticket -> {
            assertThat(ticket.assigneeId()).isEqualTo("next-admin");
            assertThat(ticket.notices()).isEmpty();
        });
        assertThat(mongo.count(Query.query(Criteria.where("roomId").is("race")), ChatMessageDocument.class))
                .isZero();
        assertThat(mongo.getCollection("admin_actions")
                        .countDocuments(new Document("targetId", "race").append("type", "SUPPORT_REPLY")))
                .isZero();
        verifyNoInteractions(publisher);
    }

    private SupportTicket seedConversation(String roomId) {
        Instant createdAt = NOW.minusSeconds(4 * 86400);
        rooms.save(SocialChatRoom.support(roomId, "requester", "안내 검증 문의", createdAt)
                .withSupportAgent(null, ADMIN));
        return saveTicket(roomId, createdAt, List.of());
    }

    private SupportTicket saveTicket(String roomId, Instant createdAt, List<SupportNotice> notices) {
        return tickets.save(new SupportTicket(
                roomId,
                "requester",
                SupportCategory.GENERAL,
                SupportStatus.IN_PROGRESS,
                ADMIN,
                createdAt,
                createdAt,
                null,
                0L,
                notices));
    }
}
