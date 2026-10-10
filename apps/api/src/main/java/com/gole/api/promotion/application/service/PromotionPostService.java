package com.gole.api.promotion.application.service;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CaptureOriginal;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.PublishNextPromotionPostUseCase;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.application.port.out.PromotionFeedbackRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import com.gole.api.promotion.application.port.out.PromotionPostEvaluationRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.application.port.out.SocialPublishPort;
import com.gole.api.promotion.domain.exception.InvalidPromotionPostStateException;
import com.gole.api.promotion.domain.exception.NoApprovedPromotionPostsException;
import com.gole.api.promotion.domain.exception.PromotionPostNotFoundException;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.exception.SourceCommitRetryLimitExceededException;
import com.gole.api.promotion.domain.model.PromotionCapture;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionFeedback;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홍보 게시물 애플리케이션 서비스 — 작성, 검토 요청, 승인/반려, 발행을 오케스트레이션한다.
 * (promotion-review)
 */
@Service
public class PromotionPostService
        implements CreatePromotionPostUseCase,
                SubmitPromotionPostForReviewUseCase,
                ManagePromotionPostsUseCase,
                PublishNextPromotionPostUseCase {

    /**
     * 같은 출처 릴리스로 만들 수 있는 초안의 상한.
     *
     * <p>반려가 점유를 놓아주는 덕에 릴리스를 다시 홍보할 수 있게 됐는데(D2), 그 반대급부로
     * 에이전트가 탐색 창(7일) 안에서 매일 같은 릴리스를 후보로 다시 집는다. 사람이 세 번 반려한
     * 릴리스는 네 번째도 반려될 가능성이 크고 그 사이 유료 모델 호출만 쌓이므로 여기서 끊는다.
     */
    private static final int MAX_DRAFTS_PER_SOURCE_COMMIT = 3;

    /** 버튼을 연달아 눌러도 피드가 한꺼번에 채워지지 않게 한다. 개별 발행과 다음 차례 발행 모두 지킨다(D24). */
    static final Duration MIN_PUBLISH_INTERVAL = Duration.ofHours(6);

    private static final String ADMIN_PATH = "/admin/promotion";

    private final PromotionPostRepositoryPort repository;
    private final PromotionPostIdGeneratorPort idGenerator;
    private final SocialPublishPort publishPort;
    private final PromotionMediaPort media;
    private final OperationalEventPublisher operationalEvents;
    private final Clock clock;
    private final PromotionFeedbackRepositoryPort feedback;
    private final PromotionPostEvaluationRepositoryPort evaluations;

    public PromotionPostService(
            PromotionPostRepositoryPort repository,
            PromotionPostIdGeneratorPort idGenerator,
            SocialPublishPort publishPort,
            PromotionMediaPort media,
            OperationalEventPublisher operationalEvents,
            Clock clock,
            PromotionFeedbackRepositoryPort feedback,
            PromotionPostEvaluationRepositoryPort evaluations) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.publishPort = publishPort;
        this.media = media;
        this.operationalEvents = operationalEvents;
        this.clock = clock;
        this.feedback = feedback;
        this.evaluations = evaluations;
    }

    @Override
    public String create(CreatePromotionPostCommand command) {
        // 미디어를 건드리기 전에 막는다 — 중복으로 거절할 초안 때문에 STAGED 이미지를
        // PUBLIC으로 전이시키면 아무도 참조하지 않는 이미지가 영구히 남는다(D8).
        // 이 검사와 저장 사이의 경쟁은 문서의 unique+sparse 인덱스가 마지막으로 막는다(D11/P11).
        if (command.sourceCommitSha() != null) {
            // 점유 기준이다 — 반려된 초안은 점유를 놓아줬으므로 여기서 걸리지 않고, 그 릴리스는
            // 다시 홍보될 수 있다(D2).
            if (repository.existsBySourceCommitSha(command.sourceCommitSha())) {
                throw new SourceCommitAlreadyPromotedException(command.sourceCommitSha());
            }
            // 점유가 풀렸다고 무한히 다시 쓰게 두지 않는다 — 출처 기준으로 센다.
            if (repository.countBySourceCommitSha(command.sourceCommitSha()) >= MAX_DRAFTS_PER_SOURCE_COMMIT) {
                throw new SourceCommitRetryLimitExceededException(
                        command.sourceCommitSha(), MAX_DRAFTS_PER_SOURCE_COMMIT);
            }
        }
        String id = idGenerator.newId();
        List<String> mediaUrls =
                command.mediaKeys().stream().map(media::publicPath).toList();
        PromotionPost draft = PromotionPost.draft(
                id,
                command.channel(),
                command.caption(),
                mediaUrls,
                command.authorId(),
                command.sourceCommitSha(),
                Instant.now(clock),
                withOriginals(command.context(), command.originals()));
        // 도메인 검증에 실패할 입력으로 미디어를 공개하지 않는다. 검증한 초안만 연결한다.
        // 다듬기 전 원본도 검토 화면이 띄우므로 게시 이미지와 같이 공개·연결한다.
        List<String> referenced = new ArrayList<>(command.mediaKeys());
        command.originals().stream()
                .filter(Objects::nonNull)
                .map(CaptureOriginal::mediaKey)
                .forEach(referenced::add);
        media.attachToPost(command.authorId(), id, referenced);
        return repository.save(draft).getId();
    }

    /** 설명표마다 다듬기 전 원본을 붙인다. 원본은 스테이지 키로 받아 공개 경로로 바꾼다. */
    private PromotionPostContext withOriginals(PromotionPostContext context, List<CaptureOriginal> originals) {
        if (originals.isEmpty()) {
            return context;
        }
        if (originals.size() != context.captures().size()) {
            throw new IllegalArgumentException("originals must match captures one to one");
        }
        List<PromotionCapture> captures = new ArrayList<>();
        for (int index = 0; index < originals.size(); index++) {
            CaptureOriginal original = originals.get(index);
            PromotionCapture capture = context.captures().get(index);
            captures.add(
                    original == null
                            ? capture
                            : capture.withOriginal(media.publicPath(original.mediaKey()), original.edit()));
        }
        return new PromotionPostContext(context.category(), captures, context.provenance());
    }

    @Override
    public PromotionPost submit(String promotionPostId) {
        PromotionPost promotionPost = getOrThrow(promotionPostId);
        // 제출 응답 유실 뒤 재시도해도 검토 시각을 바꾸거나 중복 저장하지 않는다.
        if (promotionPost.getStatus() == PromotionPostStatus.PENDING_REVIEW) {
            return promotionPost;
        }
        if (promotionPost.getStatus() == PromotionPostStatus.DRAFT
                && promotionPost.getSourceCommitSha() != null
                && promotionPost.getClaimedSourceCommitSha() == null
                && repository.existsBySourceCommitSha(promotionPost.getSourceCommitSha())) {
            throw new SourceCommitAlreadyPromotedException(promotionPost.getSourceCommitSha());
        }
        Instant now = Instant.now(clock);
        promotionPost.submitForReview(now);
        PromotionPost saved = repository.save(promotionPost);
        notifyPendingReview(saved, now);
        return saved;
    }

    /**
     * 검토 대기가 쌓이면 에이전트가 QUEUE_FULL 로 멈추므로 사람에게 바로 알린다. 캡션은 검토 전 글이라 싣지 않는다.
     * 위의 재시도 분기는 여기까지 오지 않아 같은 초안을 두 번 알리지 않는다.
     */
    private void notifyPendingReview(PromotionPost post, Instant now) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("초안 ID", post.getId());
        fields.put("분류", post.getCategory() == PromotionCategory.SERVICE ? "서비스 소개" : "기능 홍보");
        if (post.getSourceCommitSha() != null) {
            fields.put("릴리스", post.getSourceCommitSha().substring(0, 7));
        }
        fields.put("관리자 경로", ADMIN_PATH);
        operationalEvents.publish(new OperationalEvent(
                Category.ADMIN,
                Level.INFO,
                "홍보 초안 검토 대기",
                "새 홍보 초안이 검토 큐에 들어왔습니다. 관리자 화면에서 승인하거나 반려하세요.",
                fields,
                now));
    }

    @Override
    public List<PromotionPost> list(PromotionPostStatus status, int limit) {
        return repository.findRecentFirst(status, limit);
    }

    @Override
    public PromotionPost get(String promotionPostId) {
        return getOrThrow(promotionPostId);
    }

    @Override
    public boolean existsBySourceCommitSha(String sourceCommitSha) {
        return repository.existsBySourceCommitSha(sourceCommitSha);
    }

    @Override
    public PromotionPost approve(String promotionPostId, String reviewerId) {
        PromotionPost promotionPost = getOrThrow(promotionPostId);
        promotionPost.approve(reviewerId, Instant.now(clock));
        return repository.save(promotionPost);
    }

    @Override
    @Transactional
    public PromotionPost reject(String promotionPostId, String reviewerId, String reason) {
        PromotionPost promotionPost = getOrThrow(promotionPostId);
        promotionPost.reject(reviewerId, reason, Instant.now(clock));
        List<com.gole.api.promotion.domain.model.EvaluationReasonTag> tags = evaluations
                .findByPromotionPostId(promotionPostId)
                .map(evaluation -> List.copyOf(evaluation.getReasonTags()))
                .orElseGet(List::of);
        feedback.insert(PromotionFeedback.rejected(idGenerator.newId(), promotionPost, tags));
        return repository.save(promotionPost);
    }

    @Override
    public PromotionPost publish(String promotionPostId) {
        PromotionPost promotionPost = getOrThrow(promotionPostId);
        // 상태를 먼저 확인해, 잘못된 상태에서 외부(SocialPublishPort) 호출이 나가지 않게 한다.
        if (promotionPost.getStatus() != PromotionPostStatus.APPROVED) {
            throw new InvalidPromotionPostStateException(
                    promotionPostId, PromotionPostStatus.APPROVED, promotionPost.getStatus());
        }
        // 간격 판정과 갱신을 한 번에 한다 — 동시에 눌러도 한 요청만 여기를 지난다(D24).
        Instant now = Instant.now(clock);
        Instant previous = repository.claimPublishSlot(now, MIN_PUBLISH_INTERVAL);
        SocialPublishPort.PublishResult result;
        try {
            result = publishPort.publish(promotionPost);
        } catch (RuntimeException failure) {
            // 외부에 안 나갔으니 슬롯을 돌려줘 바로 다시 시도할 수 있게 한다. 아래 저장 실패는
            // 이미 나간 뒤라 돌려주지 않는다 — 돌려주면 같은 글이 또 나간다.
            repository.releasePublishSlot(now, previous);
            throw failure;
        }
        promotionPost.markPublished(result.externalPostId(), now);
        return repository.save(promotionPost);
    }

    @Override
    public PromotionPost publishNext() {
        PromotionPost next = repository.findOldestApproved().orElseThrow(NoApprovedPromotionPostsException::new);
        return publish(next.getId());
    }

    private PromotionPost getOrThrow(String promotionPostId) {
        return repository
                .findById(promotionPostId)
                .orElseThrow(() -> new PromotionPostNotFoundException(promotionPostId));
    }
}
