package com.gole.api.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.adapter.out.persistence.PromotionRunDocument;
import com.gole.api.promotion.adapter.out.persistence.PromotionRunMongoRepository;
import com.gole.api.promotion.adapter.out.persistence.PromotionRunPersistenceAdapter;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveredImage;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveryCommand;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DraftDelivery;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.Image;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import com.gole.api.promotion.application.service.PromotionDeliveryService;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 납품 창구(D29)의 runKey 선점 → 결과로 교체가 실제 Mongo 의 unique 인덱스 위에서 한 줄로 끝나는지 본다. */
@Testcontainers
class PromotionDeliveryIntegrationTest {
    private static final String BOT = "bot-1";
    private static final String SHA = "a".repeat(40);
    private static final String RUN_KEY = "gh-9-1";
    private static final Image PNG = new Image(new byte[] {1}, "image/png", "promotion.png");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static PromotionRunMongoRepository documents;
    static PromotionRunPersistenceAdapter runs;

    private final ManagePromotionPostsUseCase posts = mock(ManagePromotionPostsUseCase.class);
    private final CreatePromotionPostUseCase create = mock(CreatePromotionPostUseCase.class);
    private final SubmitPromotionPostForReviewUseCase submit = mock(SubmitPromotionPostForReviewUseCase.class);
    private final PromotionMediaPort media = mock(PromotionMediaPort.class);
    private PromotionDeliveryService service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "gole_promotion_delivery_test");
        documents = new MongoRepositoryFactory(mongo).getRepository(PromotionRunMongoRepository.class);
        var resolver =
                new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        resolver.resolveIndexFor(PromotionRunDocument.class)
                .forEach(index -> mongo.indexOps(PromotionRunDocument.class).ensureIndex(index));
        runs = new PromotionRunPersistenceAdapter(documents);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.close();
    }

    @BeforeEach
    void setUp() {
        documents.deleteAll();
        when(posts.list(eq(PromotionPostStatus.PENDING_REVIEW), anyInt())).thenReturn(List.of());
        when(media.upload(eq(BOT), any(), any(), any())).thenReturn("images/polished.png");
        when(create.create(any())).thenReturn("post-1");
        service = new PromotionDeliveryService(
                mock(ManagePromotionMemoryUseCase.class),
                posts,
                create,
                submit,
                runs,
                () -> UUID.randomUUID().toString(),
                media,
                Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC));
    }

    private static DeliveryCommand delivery() {
        return new DeliveryCommand(
                BOT,
                new RecordRunCommand(
                        RUN_KEY,
                        PromotionCategory.FEATURE,
                        SHA,
                        RunOutcome.SUBMITTED,
                        RunReasonCode.SUBMITTED,
                        null,
                        null,
                        null,
                        null,
                        List.of()),
                null,
                new DraftDelivery(
                        PromotionChannel.THREADS,
                        "캡션",
                        PromotionPostContext.NONE,
                        List.of(new DeliveredImage(PNG, null, null))));
    }

    @Test
    @DisplayName("선점한 원장 줄이 결과로 교체돼 runKey 당 한 줄만 남는다")
    void deliver_replacesTheClaimInPlace() {
        PromotionRun result = service.deliver(delivery());

        assertThat(documents.count()).isEqualTo(1);
        PromotionRun stored = runs.findByRunKey(RUN_KEY).orElseThrow();
        assertThat(stored.id()).isEqualTo(result.id());
        assertThat(stored.outcome()).isEqualTo(RunOutcome.SUBMITTED);
        assertThat(stored.promotionPostId()).isEqualTo("post-1");
    }

    @Test
    @DisplayName("같은 runKey 를 다시 납품하면 초안을 다시 만들지 않고 처음 원장을 돌려준다")
    void deliver_sameRunKeyAgain_isANoOp() {
        PromotionRun first = service.deliver(delivery());
        PromotionRun again = service.deliver(delivery());

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(documents.count()).isEqualTo(1);
        verify(create, times(1)).create(any());
    }

    @Test
    @DisplayName("초안 생성이 실패해도 선점 줄이 FAILED 로 교체되고 진행 중 표시가 남지 않는다")
    void deliver_failure_replacesTheClaimWithFailed() {
        when(create.create(any())).thenThrow(new IllegalStateException("boom"));

        service.deliver(delivery());

        assertThat(documents.count()).isEqualTo(1);
        PromotionRun stored = runs.findByRunKey(RUN_KEY).orElseThrow();
        assertThat(stored.outcome()).isEqualTo(RunOutcome.FAILED);
        assertThat(stored.detail()).isNotEqualTo("DELIVERY_IN_PROGRESS");
    }

    @Test
    @DisplayName("같은 runKey 납품 두 개가 동시에 와도 초안은 하나만 만든다")
    void deliver_concurrentSameRunKey_hasOneWinner() throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<PromotionRun> attempt = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return service.deliver(delivery());
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(List.of(attempt, attempt), 30, TimeUnit.SECONDS);
            assertThat(results.get(0).get().id()).isEqualTo(results.get(1).get().id());
        }
        assertThat(documents.count()).isEqualTo(1);
        verify(create, times(1)).create(any());
    }
}
