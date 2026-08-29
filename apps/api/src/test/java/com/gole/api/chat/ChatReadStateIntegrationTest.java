package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.adapter.out.persistence.AccountMongoRepository;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatReadCursorMongoRepository;
import com.gole.api.chat.adapter.out.persistence.SocialChatRoomMongoRepository;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ChatReadStateIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-30T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-30T10:00:01Z");
    private static final Instant T2 = Instant.parse("2026-08-30T10:00:02Z");

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
        registry.add("gole.media.seed-on-startup", () -> "false");
    }

    @Autowired
    ChatReadStatePort readStates;

    @Autowired
    ChatMessageMongoRepository messages;

    @Autowired
    ChatReadCursorMongoRepository cursors;

    @Autowired
    SocialChatRoomMongoRepository socialRooms;

    @Autowired
    AccountMongoRepository mongoAccounts;

    @Autowired
    AccountRepositoryPort accounts;

    @Autowired
    SocialChatService socialChats;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        messages.deleteAll();
        cursors.deleteAll();
        socialRooms.deleteAll();
        mongoAccounts.deleteAll();
        messages.saveAll(List.of(
                message("m-001", "peer", T0),
                message("m-002", "me", T1),
                message("m-003", "peer", T1),
                message("m-004", "peer", T2)));
    }

    @Test
    void countsOnlyIncomingMessagesStrictlyAfterSentAtAndIdCursor() {
        assertThat(readStates.countUnread("me", List.of("room-1"))).containsEntry("room-1", 3L);

        readStates.advance("room-1", "me", "m-002", T1, T2);

        assertThat(readStates.countUnread("me", List.of("room-1"))).containsEntry("room-1", 2L);
    }

    @RepeatedTest(10)
    void concurrentAndStaleAdvancesNeverRegressCursor() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> stale = pool.submit(() -> advanceAfter(start, done, "m-003", T1));
        Future<?> latest = pool.submit(() -> advanceAfter(start, done, "m-004", T2));
        start.countDown();

        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        stale.get(20, TimeUnit.SECONDS);
        latest.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        readStates.advance("room-1", "me", "m-001", T0, T2.plusSeconds(1));

        assertThat(readStates.countUnread("me", List.of("room-1"))).doesNotContainKey("room-1");
        assertThat(cursors.findByAccountIdAndRoomIdIn("me", List.of("room-1")))
                .singleElement()
                .satisfies(cursor -> {
                    assertThat(cursor.getLastReadMessageId()).isEqualTo("m-004");
                    assertThat(cursor.getLastReadSentAt()).isEqualTo(T2);
                });
    }

    @Test
    void invitedMemberStartsAtCurrentTailAndOnlySeesFutureMessagesAsUnread() {
        readStates.initializeAtLatest("room-1", "invitee", T2);

        assertThat(readStates.countUnread("invitee", List.of("room-1"))).doesNotContainKey("room-1");

        messages.save(message("m-005", "peer", T2.plusSeconds(1)));

        assertThat(readStates.countUnread("invitee", List.of("room-1"))).containsEntry("room-1", 1L);
    }

    @Test
    void existingLatestCursorCanBeInitializedAgainInsideTransaction() {
        readStates.initializeAtLatest("room-1", "returning-member", T2);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        ignored -> readStates.initializeAtLatest("room-1", "returning-member", T2.plusSeconds(1)));

        assertThat(readStates.countUnread("returning-member", List.of("room-1")))
                .doesNotContainKey("room-1");
        assertThat(cursors.findByAccountIdAndRoomIdIn("returning-member", List.of("room-1")))
                .singleElement()
                .satisfies(cursor -> assertThat(cursor.getLastReadMessageId()).isEqualTo("m-004"));
    }

    @Test
    void memberWhoLeavesAndIsInvitedAgainStartsAfterMessagesSentWhileAway() {
        saveUser("owner");
        saveUser("active-member");
        saveUser("returning-member");
        SocialChatRoom group = socialChats.createGroup("owner", "재초대 검증", List.of("active-member", "returning-member"));
        messages.save(message("group-001", group.id(), "owner", T1));
        readStates.initializeAtLatest(group.id(), "returning-member", T1);

        socialChats.leave(group.id(), "returning-member");
        messages.save(message("group-002", group.id(), "active-member", T2));
        SocialChatRoom rejoined = socialChats.invite(group.id(), "owner", "returning-member");

        assertThat(rejoined.memberIds()).contains("returning-member");
        assertThat(readStates.countUnread("returning-member", List.of(group.id())))
                .doesNotContainKey(group.id());

        messages.save(message("group-003", group.id(), "active-member", T2.plusSeconds(1)));
        assertThat(readStates.countUnread("returning-member", List.of(group.id())))
                .containsEntry(group.id(), 1L);
    }

    private void advanceAfter(CountDownLatch start, CountDownLatch done, String messageId, Instant sentAt) {
        try {
            start.await();
            readStates.advance("room-1", "me", messageId, sentAt, T2);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            done.countDown();
        }
    }

    private static ChatMessageDocument message(String id, String senderId, Instant sentAt) {
        return message(id, "room-1", senderId, sentAt);
    }

    private static ChatMessageDocument message(String id, String roomId, String senderId, Instant sentAt) {
        return new ChatMessageDocument(id, roomId, senderId, id, sentAt);
    }

    private void saveUser(String id) {
        accounts.save(Account.provisioned(id, new Email(id + "@gole.test"), new PasswordHash("plain:test"), Role.USER));
    }
}
