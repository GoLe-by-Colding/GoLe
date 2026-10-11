package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.application.port.out.ListingChatRoomRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoom;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 매물 채팅방 저장소. 개설 경합과 직거래 확인·완료는 조건부 갱신으로 한 번만 적용된다. */
@SpringBootTest
@Testcontainers
class ListingChatRoomPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-10T00:00:00Z");

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
        registry.add("gole.support-notification-outbox.processing-enabled", () -> "false");
    }

    @Autowired
    ListingChatRoomRepositoryPort rooms;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clear() {
        mongo.getDb().getCollection("chat_rooms").deleteMany(new Document());
    }

    @Test
    @DisplayName("같은 구매자·판매자·매물 방을 다시 만들면 먼저 저장된 방을 돌려준다")
    void createOrGetExisting_returnsFirstRoomOnDuplicate() {
        ChatRoom first = rooms.createOrGetExisting(ChatRoom.open("room-a", "listing-1", "buyer-1", "seller-1", T0));
        ChatRoom second = rooms.createOrGetExisting(
                ChatRoom.open("room-b", "listing-1", "buyer-1", "seller-1", T0.plusSeconds(1)));

        assertThat(first.id()).isEqualTo("room-a");
        assertThat(second.id()).isEqualTo("room-a");
    }

    @Test
    @DisplayName("확인은 한 번만 기록되고 양쪽 확인 뒤에만 한 번 완료된다")
    void confirmationAndCompletion_applyOnce() {
        rooms.createOrGetExisting(ChatRoom.open("room-1", "listing-1", "buyer-1", "seller-1", T0));

        assertThat(rooms.completeIfBothConfirmed("room-1", T0.plusSeconds(1))).isEmpty();
        assertThat(rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, T0.plusSeconds(2)))
                .isTrue();
        assertThat(rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, T0.plusSeconds(3)))
                .isFalse();
        assertThat(rooms.completeIfBothConfirmed("room-1", T0.plusSeconds(4))).isEmpty();
        assertThat(rooms.recordConfirmation("room-1", ChatRoom.Party.SELLER, T0.plusSeconds(5)))
                .isTrue();

        ChatRoom completed =
                rooms.completeIfBothConfirmed("room-1", T0.plusSeconds(6)).orElseThrow();
        assertThat(completed.buyerConfirmedAt()).isEqualTo(T0.plusSeconds(2));
        assertThat(completed.sellerConfirmedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(completed.directTradeCompletedAt()).isEqualTo(T0.plusSeconds(6));
        assertThat(rooms.completeIfBothConfirmed("room-1", T0.plusSeconds(7))).isEmpty();
    }

    @Test
    @DisplayName("완료 전에는 확인을 지울 수 있고 완료 뒤에는 지우지 않는다")
    void clearConfirmation_onlyBeforeCompletion() {
        rooms.createOrGetExisting(ChatRoom.open("room-1", "listing-1", "buyer-1", "seller-1", T0));
        rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, T0.plusSeconds(1));
        rooms.clearConfirmation("room-1", ChatRoom.Party.BUYER);
        assertThat(rooms.findById("room-1").orElseThrow().buyerConfirmedAt()).isNull();

        rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, T0.plusSeconds(2));
        rooms.recordConfirmation("room-1", ChatRoom.Party.SELLER, T0.plusSeconds(3));
        rooms.completeIfBothConfirmed("room-1", T0.plusSeconds(4));
        rooms.clearConfirmation("room-1", ChatRoom.Party.BUYER);
        assertThat(rooms.findById("room-1").orElseThrow().buyerConfirmedAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    @DisplayName("참여 중인 방을 마지막 활동이 최근인 순으로 상한까지 읽는다")
    void findRecentByParticipant_sortsByActivityAndLimits() {
        rooms.createOrGetExisting(ChatRoom.open("old", "listing-1", "me", "seller-1", T0));
        rooms.createOrGetExisting(ChatRoom.open("new", "listing-2", "buyer-2", "me", T0.plusSeconds(10)));
        rooms.createOrGetExisting(ChatRoom.open("mid", "listing-3", "me", "seller-3", T0.plusSeconds(5)));
        rooms.createOrGetExisting(ChatRoom.open("other", "listing-4", "buyer-9", "seller-9", T0.plusSeconds(20)));

        assertThat(rooms.findRecentByParticipant("me", 2))
                .extracting(ChatRoom::id)
                .containsExactly("new", "mid");
    }
}
