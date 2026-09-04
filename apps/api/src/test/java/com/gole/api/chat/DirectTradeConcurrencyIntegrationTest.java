package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.application.DirectTradeService;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.listing.adapter.out.persistence.ListingMongoRepository;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.notification.adapter.out.persistence.NotificationDocument;
import com.gole.api.notification.adapter.out.persistence.NotificationMongoRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DirectTradeConcurrencyIntegrationTest {

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
    DirectTradeService directTrades;

    @Autowired
    CreateListingUseCase createListing;

    @Autowired
    GetListingUseCase getListing;

    @Autowired
    ChatRoomMongoRepository rooms;

    @Autowired
    ListingMongoRepository listings;

    @Autowired
    NotificationMongoRepository notifications;

    @Autowired
    ManageMediaAssetsUseCase mediaAssets;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        rooms.deleteAll();
        listings.deleteAll();
    }

    @RepeatedTest(10)
    void simultaneousBuyerAndSellerConfirmationsCompleteWithoutTransientFailure() throws Exception {
        String listingId = createListing.create(new CreateListingCommand(
                "seller-1",
                "직거래 동시 확인 세트",
                "Mongo 트랜잭션 재시도 통합 검증",
                100_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of(stagedPhoto("seller-1")),
                "10307"));
        String roomId = "direct-race-" + listingId;
        rooms.save(new ChatRoomDocument(roomId, listingId, "buyer-1", "seller-1", Instant.now()));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ChatRoomDocument> buyer = pool.submit(() -> confirmAfter(start, roomId, "buyer-1"));
            Future<ChatRoomDocument> seller = pool.submit(() -> confirmAfter(start, roomId, "seller-1"));

            start.countDown();
            buyer.get(30, TimeUnit.SECONDS);
            seller.get(30, TimeUnit.SECONDS);

            ChatRoomDocument completed = rooms.findById(roomId).orElseThrow();
            assertThat(completed.getBuyerConfirmedAt()).isNotNull();
            assertThat(completed.getSellerConfirmedAt()).isNotNull();
            assertThat(completed.getDirectTradeCompletedAt()).isNotNull();
            assertThat(getListing.getById(listingId).getStatus()).isEqualTo(ListingStatus.SOLD);

            List<NotificationDocument> delivered = notifications.findAll();
            assertThat(delivered).extracting(NotificationDocument::getLink).containsOnly("/chat?room=" + roomId);
            assertThat(delivered)
                    .extracting(NotificationDocument::getMessage)
                    .containsExactlyInAnyOrder("상대방이 직거래 완료를 확인했어요. 거래 내용을 확인해 주세요", "양쪽 확인이 끝나 직거래가 완료됐어요");
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void simultaneousFinalConfirmationAndCancellationResolveWithoutLeakingWriteConflict() throws Exception {
        String listingId = createListing.create(new CreateListingCommand(
                "seller-1",
                "직거래 확인 취소 경합 세트",
                "Mongo 확인 취소 재시도 통합 검증",
                100_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of(stagedPhoto("seller-1")),
                "10307"));
        String roomId = "direct-confirm-cancel-" + listingId;
        rooms.save(new ChatRoomDocument(roomId, listingId, "buyer-1", "seller-1", Instant.now()));
        directTrades.confirm(roomId, "buyer-1");
        notifications.deleteAll();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ChatRoomDocument> finalConfirmation = pool.submit(() -> confirmAfter(start, roomId, "seller-1"));
            Future<ChatRoomDocument> cancellation = pool.submit(() -> cancelAfter(start, roomId, "buyer-1"));

            start.countDown();
            Outcome confirmOutcome = await(finalConfirmation);
            Outcome cancelOutcome = await(cancellation);
            ChatRoomDocument finalRoom = rooms.findById(roomId).orElseThrow();

            assertThat(confirmOutcome.failure()).isNull();
            if (finalRoom.getDirectTradeCompletedAt() != null) {
                assertThat(cancelOutcome.failure()).isInstanceOf(ConflictException.class);
                assertThat(getListing.getById(listingId).getStatus()).isEqualTo(ListingStatus.SOLD);
            } else {
                assertThat(cancelOutcome.failure()).isNull();
                assertThat(finalRoom.getBuyerConfirmedAt()).isNull();
                assertThat(finalRoom.getSellerConfirmedAt()).isNotNull();
                assertThat(getListing.getById(listingId).getStatus()).isEqualTo(ListingStatus.ACTIVE);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private ChatRoomDocument confirmAfter(CountDownLatch start, String roomId, String actorId) throws Exception {
        start.await(20, TimeUnit.SECONDS);
        return directTrades.confirm(roomId, actorId);
    }

    private ChatRoomDocument cancelAfter(CountDownLatch start, String roomId, String actorId) throws Exception {
        start.await(20, TimeUnit.SECONDS);
        return directTrades.cancelConfirmation(roomId, actorId);
    }

    private String stagedPhoto(String ownerId) {
        String key = "images/" + UUID.randomUUID() + ".jpg";
        mediaAssets.registerStaged(ownerId, key, "image/jpeg", 1);
        return key;
    }

    private static Outcome await(Future<ChatRoomDocument> future) throws Exception {
        try {
            return new Outcome(future.get(30, TimeUnit.SECONDS), null);
        } catch (ExecutionException failure) {
            return new Outcome(null, failure.getCause());
        }
    }

    private record Outcome(ChatRoomDocument result, Throwable failure) {}
}
