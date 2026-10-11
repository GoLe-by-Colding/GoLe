package com.gole.api.promotion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.promotion.adapter.out.persistence.*;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase.*;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import com.gole.api.promotion.application.port.out.PromotionPostEvaluationRepositoryPort;
import com.gole.api.promotion.application.port.out.SocialPublishPort;
import com.gole.api.promotion.application.service.PromotionMemoryService;
import com.gole.api.promotion.application.service.PromotionPostService;
import com.gole.api.promotion.domain.model.*;
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
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 replica set 트랜잭션으로 반려/기억의 원자성과 동시성을 확인한다. */
@Testcontainers
class PromotionMemoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static MongoTransactionManager transactions;
    static PromotionPostPersistenceAdapter posts;
    static PromotionFeedbackPersistenceAdapter feedback;
    static PromotionGuidelinePersistenceAdapter guidelines;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "promotion_memory_test");
        mongo = new MongoTemplate(factory);
        transactions = new MongoTransactionManager(factory);
        var resolver =
                new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        for (Class<?> type : List.of(
                PromotionPostDocument.class, PromotionFeedbackDocument.class, PromotionGuidelineDocument.class)) {
            resolver.resolveIndexFor(type).forEach(index -> mongo.indexOps(type).ensureIndex(index));
        }
        posts = new PromotionPostPersistenceAdapter(
                new MongoRepositoryFactory(mongo).getRepository(PromotionPostMongoRepository.class), mongo);
        feedback = new PromotionFeedbackPersistenceAdapter(mongo);
        guidelines = new PromotionGuidelinePersistenceAdapter(mongo);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.close();
    }

    @BeforeEach
    void clean() {
        mongo.remove(new Query(), PromotionFeedbackDocument.class);
        mongo.remove(new Query(), PromotionGuidelineDocument.class);
        mongo.remove(new Query(), PromotionPostDocument.class);
    }

    private static <T> T transactional(T target) {
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked")
        T result = (T) proxy.getProxy();
        return result;
    }

    private PromotionPostService postService(
            PromotionFeedbackPersistenceAdapter feedbackRepo, PromotionPostPersistenceAdapter postRepo) {
        return transactional(new PromotionPostService(
                postRepo,
                () -> UUID.randomUUID().toString(),
                mock(SocialPublishPort.class),
                mock(PromotionMediaPort.class),
                mock(OperationalEventPublisher.class),
                CLOCK,
                feedbackRepo,
                mock(PromotionPostEvaluationRepositoryPort.class)));
    }

    private PromotionMemoryService memory(PromotionFeedbackPersistenceAdapter feedbackRepo) {
        return transactional(new PromotionMemoryService(
                feedbackRepo, guidelines, () -> UUID.randomUUID().toString(), CLOCK));
    }

    private PromotionPost pending(String id, PromotionCategory category, String route) {
        var capture = new PromotionCapture(
                "화면",
                route,
                "",
                CaptureDataSource.DEMO,
                NOW,
                "/api/v1/media/images/original.png",
                "편집 지침 ".repeat(200));
        var post = PromotionPost.draft(
                id,
                PromotionChannel.THREADS,
                "반려 당시 캡션",
                List.of("/api/v1/media/images/final.png"),
                "agent",
                null,
                NOW,
                new PromotionPostContext(category, List.of(capture), null));
        post.submitForReview(NOW);
        posts.save(post);
        return post;
    }

    private PromotionFeedback source(String id, PromotionCategory category, String route, Instant when) {
        var post = pending("post-" + id, category, route);
        post.reject("human", "화면 글자가 작아서 읽기 어렵다", when);
        var result = PromotionFeedback.rejected(id, post, List.of(EvaluationReasonTag.SCREEN_MISMATCH));
        feedback.insert(result);
        return result;
    }

    private Proposal proposal(String sourceId) {
        return new Proposal(
                PromotionGuidelineKind.PROCEDURE,
                "가독성을 우선한다",
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                List.of(PromotionCategory.FEATURE, PromotionCategory.SERVICE),
                List.of(sourceId));
    }

    @Test
    @DisplayName("반려 후 재제출해도 캡션·최종/원본 이미지·긴 편집 지시문과 사유는 보존한다")
    void reject_snapshotSurvivesResubmit() {
        pending("p1", PromotionCategory.SERVICE, "/market");
        var service = postService(feedback, posts);
        service.reject("p1", "human", "다시 작성");
        service.submit("p1");

        var saved = feedback.findRecent("p1", 10).getFirst();
        assertThat(saved.reason()).isEqualTo("다시 작성");
        assertThat(saved.snapshot().caption()).isEqualTo("반려 당시 캡션");
        assertThat(saved.snapshot().mediaUrls()).containsExactly("/api/v1/media/images/final.png");
        assertThat(saved.snapshot().captures().getFirst().originalUrl()).endsWith("original.png");
        assertThat(saved.snapshot().captures().getFirst().edit()).hasSizeGreaterThan(1000);
        assertThat(posts.findById("p1").orElseThrow().getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("피드백 insert 뒤 실패하면 반려와 기록이 모두 롤백된다")
    void reject_feedbackFailureRollsBackBothWrites() {
        pending("p1", PromotionCategory.SERVICE, "/market");
        var failing = spy(feedback);
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new IllegalStateException("feedback unavailable");
                })
                .when(failing)
                .insert(any());

        assertThatThrownBy(() -> postService(failing, posts).reject("p1", "human", "사유"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(posts.findById("p1").orElseThrow().getStatus()).isEqualTo(PromotionPostStatus.PENDING_REVIEW);
        assertThat(feedback.findRecent(null, 10)).isEmpty();
    }

    @Test
    @DisplayName("게시물 저장 실패도 이미 추가한 피드백을 롤백한다")
    void reject_postFailureRollsBackFeedback() {
        pending("p1", PromotionCategory.SERVICE, "/market");
        var failing = spy(posts);
        doThrow(new IllegalStateException("post unavailable")).when(failing).save(any());
        assertThatThrownBy(() -> postService(feedback, failing).reject("p1", "human", "사유"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(posts.findById("p1").orElseThrow().getStatus()).isEqualTo(PromotionPostStatus.PENDING_REVIEW);
        assertThat(feedback.findRecent(null, 10)).isEmpty();
    }

    @Test
    @DisplayName("같은 게시물을 동시에 반려해도 한 반려와 한 경험만 남는다")
    void reject_concurrentReviewsHaveOneWinner() throws Exception {
        pending("p1", PromotionCategory.SERVICE, "/market");
        var barrier = new CyclicBarrier(2);
        var racingPosts = spy(posts);
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    barrier.await(15, TimeUnit.SECONDS);
                    return result;
                })
                .when(racingPosts)
                .findById("p1");
        var service = postService(feedback, racingPosts);
        Callable<Boolean> attempt = () -> {
            try {
                service.reject("p1", "human", "사유");
                return true;
            } catch (RuntimeException expected) {
                return false;
            }
        };
        assertOneWinner(attempt, attempt);
        assertThat(feedback.findRecent("p1", 10)).hasSize(1);
        assertThat(posts.findById("p1").orElseThrow().getStatus()).isEqualTo(PromotionPostStatus.DRAFT);
    }

    @Test
    @DisplayName("성찰은 멱등이며 부분집합 재시도는 거부하고 사람 확정 후에만 활성 기억에 나온다")
    void reflect_exactBatchAndHumanActivation() {
        source("f1", PromotionCategory.SERVICE, "/market", NOW);
        source("f2", PromotionCategory.FEATURE, "/search", NOW);
        var service = memory(feedback);
        var command = new ReflectionCommand(List.of("f1", "f2"), "run-1", List.of(proposal("f1")));
        var first = service.reflect("agent", command);
        assertThat(service.reflect("agent", command)).isEqualTo(first);
        assertThatThrownBy(() -> service.reflect("agent", new ReflectionCommand(List.of("f1"), "run-1", List.of())))
                .isInstanceOf(ConflictException.class);
        assertThat(service.context(PromotionCategory.SERVICE, List.of("/market"))
                        .guidelines())
                .isEmpty();
        var id = first.getFirst().id();
        var edited = service.edit(
                id,
                "화면을 읽을 수 있는 크기를 유지한다",
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                List.of(PromotionCategory.SERVICE),
                first.getFirst().version());
        service.activate(id, "human", edited.version());
        assertThat(service.context(PromotionCategory.SERVICE, List.of()).guidelines())
                .hasSize(1);
        assertThat(service.context(PromotionCategory.FEATURE, List.of()).guidelines())
                .isEmpty();
        service.retire(id);
        assertThat(service.context(PromotionCategory.SERVICE, List.of()).guidelines())
                .isEmpty();
    }

    @Test
    @DisplayName("빈 제안도 완료로 저장하고 다시 실행해도 지침을 만들지 않는다")
    void reflect_emptyBatchIsIdempotent() {
        source("f1", PromotionCategory.SERVICE, "/market", NOW);
        var command = new ReflectionCommand(List.of("f1"), "run-1", List.of());
        assertThat(memory(feedback).reflect("agent", command)).isEmpty();
        assertThat(memory(feedback).reflect("agent", command)).isEmpty();
        assertThat(feedback.findUnreflected(3)).isEmpty();
    }

    @Test
    @DisplayName("성찰 처리 표시 저장에 실패하면 지침과 처리 표시가 함께 롤백된다")
    void reflect_markFailureRollsBackProposals() {
        source("f1", PromotionCategory.SERVICE, "/market", NOW);
        var failing = spy(feedback);
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new IllegalStateException("mark failed");
                })
                .when(failing)
                .markReflected(any(), any(), any(), anyBoolean());
        assertThatThrownBy(() -> memory(failing)
                        .reflect("agent", new ReflectionCommand(List.of("f1"), "run-1", List.of(proposal("f1")))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(guidelines.findRecent(null, 10)).isEmpty();
        assertThat(feedback.findUnreflected(3)).hasSize(1);
    }

    @Test
    @DisplayName("서로 다른 반려 묶음이 같은 runKey로 동시에 처리되면 한 묶음만 저장된다")
    void reflect_concurrentDifferentBatchesShareNoRunKey() throws Exception {
        source("f1", PromotionCategory.SERVICE, "/market", NOW);
        source("f2", PromotionCategory.SERVICE, "/search", NOW);
        var barrier = new CyclicBarrier(2);
        var racing = spy(feedback);
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    barrier.await(15, TimeUnit.SECONDS);
                    return result;
                })
                .when(racing)
                .findByReflectedRunKey("run-1");
        var service = memory(racing);
        Callable<Boolean> first = () -> reflectAttempt(service, "f1");
        Callable<Boolean> second = () -> reflectAttempt(service, "f2");
        assertOneWinner(first, second);
        assertThat(feedback.findByReflectedRunKey("run-1")).hasSize(1);
        assertThat(guidelines.findByReflectionRunKey("run-1")).hasSize(1);
        assertThat(feedback.findUnreflected(3)).hasSize(1);
    }

    @Test
    @DisplayName("최근 50건에서 밀려난 반려도 지침 근거 ID로 조회할 수 있다")
    void feedback_readsSourceOutsideRecentList() {
        var historical = source("old", PromotionCategory.SERVICE, "/market", NOW.minusSeconds(1));
        for (int index = 0; index < 50; index++) {
            source("recent-" + index, PromotionCategory.SERVICE, "/market", NOW.plusSeconds(index));
        }
        var service = memory(feedback);

        assertThat(service.listFeedback(null, 50))
                .extracting(PromotionFeedback::id)
                .doesNotContain("old");
        assertThat(service.getFeedback("old")).isEqualTo(historical);
    }

    @Test
    @DisplayName("관련 경로 우선/최신순으로 반려 3건과 활성 지침 8개만 선택한다")
    void context_selectsRelevantWithinBudget() {
        source("match", PromotionCategory.SERVICE, "/market", NOW.minusSeconds(100));
        for (int index = 0; index < 5; index++) {
            source("other" + index, PromotionCategory.SERVICE, "/search", NOW.plusSeconds(index));
        }
        source("feature", PromotionCategory.FEATURE, "/market", NOW.plusSeconds(100));
        for (int index = 0; index < 10; index++) {
            var guideline = new PromotionGuideline(
                    "g" + index,
                    PromotionGuidelineKind.PROCEDURE,
                    "화면 가독성",
                    List.of(PromotionMemoryTarget.IMAGE_EDIT),
                    List.of(PromotionCategory.SERVICE),
                    List.of("match"),
                    PromotionGuidelineStatus.PROPOSED,
                    "agent",
                    NOW,
                    NOW.plusSeconds(index),
                    null,
                    null,
                    "run-" + index,
                    0);
            guidelines.insert(guideline.activate("human", NOW.plusSeconds(index)));
        }
        var context = memory(feedback).context(PromotionCategory.SERVICE, List.of("/market"));
        assertThat(context.feedback()).extracting(PromotionFeedback::id).containsExactly("match", "other4", "other3");
        assertThat(context.guidelines()).hasSize(8);
        assertThat(context.guidelines().getFirst().id()).isEqualTo("g9");
        assertThat(context.unreflectedFeedback()).hasSize(3);
    }

    private PromotionGuideline proposedGuideline() {
        var guideline = new PromotionGuideline(
                "g1",
                PromotionGuidelineKind.PROCEDURE,
                "원래 내용",
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                List.of(PromotionCategory.SERVICE),
                List.of("f1"),
                PromotionGuidelineStatus.PROPOSED,
                "agent",
                NOW,
                NOW,
                null,
                null,
                "run-1",
                0);
        guidelines.insert(guideline);
        return guideline;
    }

    @Test
    @DisplayName("A 수정 후 B 수정이 끼면 A가 보지 못한 내용을 확정하거나 덮어쓸 수 없다")
    void guideline_staleReviewCannotApproveUnseenContent() {
        var original = proposedGuideline();
        var service = memory(feedback);
        var a = service.edit("g1", "A 검토 내용", original.targets(), original.categories(), 0);
        var b = service.edit("g1", "B 변경 내용", a.targets(), a.categories(), a.version());
        assertThatThrownBy(() -> service.activate("g1", "human-a", a.version())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.edit("g1", "A 덮어쓰기", a.targets(), a.categories(), a.version()))
                .isInstanceOf(ConflictException.class);
        assertThat(guidelines.findById("g1").orElseThrow()).isEqualTo(b);
        assertThat(b.version()).isEqualTo(2);
        assertThat(b.updatedAt()).isEqualTo(a.updatedAt());
        assertThat(b.confirmedBy()).isNull();
        assertThat(service.activate("g1", "human-a", b.version()).content()).isEqualTo(b.content());
    }

    @Test
    @DisplayName("같은 버전을 함께 읽은 수정과 확정은 한 요청만 성공하고 패자는 버전 충돌이다")
    void guideline_concurrentEditAndActivationHaveOneWinner() throws Exception {
        var original = proposedGuideline();
        var barrier = new CyclicBarrier(2);
        var racing = spy(guidelines);
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    barrier.await(15, TimeUnit.SECONDS);
                    return result;
                })
                .when(racing)
                .findById("g1");
        var service = transactional(new PromotionMemoryService(
                feedback, racing, () -> UUID.randomUUID().toString(), CLOCK));
        Callable<Boolean> edit = () -> {
            try {
                service.edit("g1", "경합 수정", original.targets(), original.categories(), 0);
                return true;
            } catch (ConflictException expected) {
                assertThat(expected.getCode()).isEqualTo("PROMOTION_GUIDELINE_VERSION_CONFLICT");
                return false;
            }
        };
        Callable<Boolean> activate = () -> {
            try {
                service.activate("g1", "human", 0);
                return true;
            } catch (ConflictException expected) {
                assertThat(expected.getCode()).isEqualTo("PROMOTION_GUIDELINE_VERSION_CONFLICT");
                return false;
            }
        };
        assertOneWinner(edit, activate);
        var saved = guidelines.findById("g1").orElseThrow();
        assertThat(saved.version()).isEqualTo(1);
        if (saved.status() == PromotionGuidelineStatus.ACTIVE) {
            assertThat(saved.content()).isEqualTo(original.content());
            assertThat(saved.confirmedBy()).isEqualTo("human");
        } else {
            assertThat(saved.content()).isEqualTo("경합 수정");
            assertThat(saved.confirmedBy()).isNull();
        }
    }

    @Test
    @DisplayName("버전 없는 과거 문서는 0으로 읽고 첫 수정부터 Int64 버전을 원자적으로 저장한다")
    void guideline_legacyDocumentInitializesVersionOnEdit() {
        var original = proposedGuideline();
        var query = org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id")
                        .is("g1"));
        mongo.updateFirst(
                query,
                new org.springframework.data.mongodb.core.query.Update().unset("version"),
                PromotionGuidelineDocument.class);
        assertThat(guidelines.findById("g1").orElseThrow().version()).isZero();
        var edited = memory(feedback).edit("g1", "기존 문서 수정", original.targets(), original.categories(), 0);
        assertThat(edited.version()).isEqualTo(1);
        assertThat(mongo.getCollection("promotion_guidelines")
                        .find(new org.bson.Document("_id", "g1"))
                        .first()
                        .get("version"))
                .isEqualTo(1L);
        assertThat(edited.proposedBy()).isEqualTo(original.proposedBy());
        assertThat(edited.sourceFeedbackIds()).isEqualTo(original.sourceFeedbackIds());
    }

    private boolean reflectAttempt(PromotionMemoryService service, String id) {
        try {
            service.reflect("agent", new ReflectionCommand(List.of(id), "run-1", List.of(proposal(id))));
            return true;
        } catch (RuntimeException expected) {
            return false;
        }
    }

    private void assertOneWinner(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(List.of(first, second), 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
    }
}
