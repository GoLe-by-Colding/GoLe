package com.gole.api.promotion.application.service;

import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaKey;
import com.gole.api.media.domain.model.MediaTargetType;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.application.port.out.SocialPublishPort;
import com.gole.api.promotion.domain.exception.InvalidPromotionPostStateException;
import com.gole.api.promotion.domain.exception.PromotionPostNotFoundException;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.exception.SourceCommitRetryLimitExceededException;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 홍보 게시물 애플리케이션 서비스 — 작성, 검토 요청, 승인/반려, 발행을 오케스트레이션한다.
 * (promotion-review)
 */
@Service
public class PromotionPostService
        implements CreatePromotionPostUseCase, SubmitPromotionPostForReviewUseCase, ManagePromotionPostsUseCase {

    /**
     * 같은 출처 릴리스로 만들 수 있는 초안의 상한.
     *
     * <p>반려가 점유를 놓아주는 덕에 릴리스를 다시 홍보할 수 있게 됐는데(D2), 그 반대급부로
     * 에이전트가 탐색 창(7일) 안에서 매일 같은 릴리스를 후보로 다시 집는다. 사람이 세 번 반려한
     * 릴리스는 네 번째도 반려될 가능성이 크고 그 사이 유료 모델 호출만 쌓이므로 여기서 끊는다.
     */
    private static final int MAX_DRAFTS_PER_SOURCE_COMMIT = 3;

    private final PromotionPostRepositoryPort repository;
    private final PromotionPostIdGeneratorPort idGenerator;
    private final SocialPublishPort publishPort;
    private final ManageMediaAssetsUseCase mediaAssets;
    private final Clock clock;

    public PromotionPostService(
            PromotionPostRepositoryPort repository,
            PromotionPostIdGeneratorPort idGenerator,
            SocialPublishPort publishPort,
            ManageMediaAssetsUseCase mediaAssets,
            Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.publishPort = publishPort;
        this.mediaAssets = mediaAssets;
        this.clock = clock;
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
        // media 컨텍스트의 인바운드 포트만 의존한다 — STAGED(업로더 전용, 24시간 뒤 폐기)를
        // 이 게시물에 연결해 PUBLIC으로 전이시키지 않으면, 검토자가 첨부 이미지를 못 보고
        // 하루 뒤 원본이 삭제된다(promotion-review D8).
        mediaAssets.replaceReferences(
                command.authorId(), MediaTargetType.PROMOTION_POST, id, command.mediaKeys(), true);
        List<String> mediaUrls =
                command.mediaKeys().stream().map(MediaKey::publicPath).toList();
        PromotionPost draft = PromotionPost.draft(
                id,
                command.channel(),
                command.caption(),
                mediaUrls,
                command.authorId(),
                command.sourceCommitSha(),
                Instant.now(clock));
        return repository.save(draft).getId();
    }

    @Override
    public PromotionPost submit(String promotionPostId) {
        PromotionPost promotionPost = getOrThrow(promotionPostId);
        promotionPost.submitForReview(Instant.now(clock));
        return repository.save(promotionPost);
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
    public PromotionPost reject(String promotionPostId, String reviewerId, String reason) {
        PromotionPost promotionPost = getOrThrow(promotionPostId);
        promotionPost.reject(reviewerId, reason, Instant.now(clock));
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
        SocialPublishPort.PublishResult result = publishPort.publish(promotionPost);
        promotionPost.markPublished(result.externalPostId(), Instant.now(clock));
        return repository.save(promotionPost);
    }

    private PromotionPost getOrThrow(String promotionPostId) {
        return repository
                .findById(promotionPostId)
                .orElseThrow(() -> new PromotionPostNotFoundException(promotionPostId));
    }
}
