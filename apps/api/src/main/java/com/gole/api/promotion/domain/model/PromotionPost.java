package com.gole.api.promotion.domain.model;

import com.gole.api.promotion.domain.exception.InvalidPromotionPostStateException;
import com.gole.api.promotion.domain.exception.SelfReviewNotAllowedException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 홍보 게시물 애그리거트 — Threads 등 외부 채널에 올릴 초안의 작성·검토·발행 상태 전이를
 * 캡슐화한다. 업로드 전 관리자 검토 게이트의 단일 진실 공급원(promotion-review).
 */
public final class PromotionPost {

    private static final int MAX_CAPTION_LENGTH = 500;
    private static final int MAX_MEDIA_COUNT = 10;
    private static final Pattern COMMIT_SHA_PATTERN = Pattern.compile("[0-9a-f]{40}");

    private final String id;
    private final PromotionChannel channel;
    private final String caption;
    private final List<String> mediaUrls;
    private final String authorId;

    /** 출처 — 이 초안이 어느 릴리스에서 나왔나. 불변이라 반려돼도 남고, 화면 표시·집계가 이것을 본다. */
    private final String sourceCommitSha;

    /**
     * 점유 — 지금 이 릴리스를 붙잡고 있나. 생성 시 {@code sourceCommitSha}와 같은 값으로 시작하고
     * 반려되면 null로 놓아준다(D2).
     *
     * <p><b>왜 출처와 나눴나.</b> 한 필드가 둘을 겸하면 반려된 초안이 그 릴리스를 영구히 잠근다 —
     * 에이전트는 {@code /exists}가 참이라 다시 만들지 않고, 사람이 같은 SHA로 만들려 하면 DB
     * unique 인덱스가 막는데 초안을 고칠 엔드포인트도 없다. 둘을 나누면 "어디서 나왔나"는 남기고
     * "아직 붙잡고 있나"만 놓아줄 수 있다.
     */
    private String claimedSourceCommitSha;

    private PromotionPostStatus status;
    private final Instant createdAt;
    private Instant submittedAt;
    private String reviewerId;
    private Instant reviewedAt;
    private String rejectionReason;
    private Instant publishedAt;
    private String externalPostId;

    public PromotionPost(
            String id,
            PromotionChannel channel,
            String caption,
            List<String> mediaUrls,
            String authorId,
            String sourceCommitSha,
            String claimedSourceCommitSha,
            PromotionPostStatus status,
            Instant createdAt,
            Instant submittedAt,
            String reviewerId,
            Instant reviewedAt,
            String rejectionReason,
            Instant publishedAt,
            String externalPostId) {
        this.id = Objects.requireNonNull(id, "id");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.caption = requireCaption(caption);
        this.mediaUrls = requireMediaUrls(mediaUrls);
        this.authorId = requireText(authorId, "authorId");
        this.sourceCommitSha = requireSourceCommitSha(sourceCommitSha);
        // 점유는 저장된 값을 그대로 되살린다 — 반려된 초안은 출처만 남고 점유는 null이다.
        this.claimedSourceCommitSha = requireSourceCommitSha(claimedSourceCommitSha);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.submittedAt = submittedAt;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
        this.publishedAt = publishedAt;
        this.externalPostId = externalPostId;
    }

    /** 신규 초안: DRAFT 상태로 생성. */
    public static PromotionPost draft(
            String id,
            PromotionChannel channel,
            String caption,
            List<String> mediaUrls,
            String authorId,
            String sourceCommitSha,
            Instant now) {
        return new PromotionPost(
                id,
                channel,
                caption,
                mediaUrls,
                authorId,
                sourceCommitSha,
                // 새 초안은 자기 출처 릴리스를 곧바로 점유한다. 반려될 때 이 값만 풀린다.
                sourceCommitSha,
                PromotionPostStatus.DRAFT,
                now,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** 검토 요청: DRAFT → PENDING_REVIEW. DRAFT가 아니면 거부한다. */
    public void submitForReview(Instant now) {
        requireStatus(PromotionPostStatus.DRAFT);
        Objects.requireNonNull(now, "now");
        this.status = PromotionPostStatus.PENDING_REVIEW;
        this.claimedSourceCommitSha = sourceCommitSha;
        this.submittedAt = now;
        this.reviewerId = null;
        this.reviewedAt = null;
        this.rejectionReason = null;
    }

    /** 승인: PENDING_REVIEW → APPROVED. 작성자 본인은 승인할 수 없다(D4). */
    public void approve(String reviewerId, Instant now) {
        requireStatus(PromotionPostStatus.PENDING_REVIEW);
        requireNotAuthor(reviewerId);
        this.status = PromotionPostStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 반려: PENDING_REVIEW → DRAFT. 사유를 남기고 작성자가 고쳐 다시 제출할 수 있게 한다.
     *
     * <p><b>이때 릴리스 점유를 놓아준다</b>(D2). 반려된 초안은 그 릴리스를 홍보한 것이 아니므로
     * 계속 붙잡고 있는 것이 사실과 어긋나고, 무엇보다 붙잡고 있으면 그 릴리스가 영영 홍보되지
     * 않는다 — 에이전트는 {@code /exists}가 참이라 건너뛰고, 사람이 같은 SHA로 다시 만들려 하면
     * 409로 막힌다. 출처({@code sourceCommitSha})는 불변이라 그대로 남으므로 화면 표시와 집계는
     * 깨지지 않는다.
     */
    public void reject(String reviewerId, String reason, Instant now) {
        requireStatus(PromotionPostStatus.PENDING_REVIEW);
        requireNotAuthor(reviewerId);
        this.status = PromotionPostStatus.DRAFT;
        this.claimedSourceCommitSha = null;
        this.reviewerId = reviewerId;
        this.reviewedAt = Objects.requireNonNull(now, "now");
        this.rejectionReason = requireText(reason, "reason");
    }

    /** 발행 완료 처리: APPROVED → PUBLISHED. 실제 호출은 SocialPublishPort가 담당하고, 여기서는 결과만 반영한다. */
    public void markPublished(String externalPostId, Instant now) {
        requireStatus(PromotionPostStatus.APPROVED);
        this.status = PromotionPostStatus.PUBLISHED;
        this.externalPostId = requireText(externalPostId, "externalPostId");
        this.publishedAt = Objects.requireNonNull(now, "now");
    }

    private void requireStatus(PromotionPostStatus expected) {
        if (status != expected) {
            throw new InvalidPromotionPostStateException(id, expected, status);
        }
    }

    private void requireNotAuthor(String reviewerId) {
        if (Objects.equals(authorId, reviewerId)) {
            throw new SelfReviewNotAllowedException(id);
        }
    }

    private static String requireCaption(String value) {
        String text = requireText(value, "caption");
        if (text.length() > MAX_CAPTION_LENGTH) {
            throw new IllegalArgumentException("caption must be at most " + MAX_CAPTION_LENGTH + " chars");
        }
        return text;
    }

    private static String requireSourceCommitSha(String value) {
        if (value == null) {
            return null;
        }
        if (!COMMIT_SHA_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("sourceCommitSha must be a lowercase 40-character hex string");
        }
        return value;
    }

    /** 미디어 URL은 선택이지만, 넣었다면 빈 문자열이 섞이지 않아야 하고 개수 상한을 지켜야 한다.
     *  (T3 — 이미지 첨부. 발행 미리보기용이며 개수 제한은 {@code MediaController}의 배치 업로드
     *  상한과 맞춘다.) */
    private static List<String> requireMediaUrls(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return List.of();
        }
        if (mediaUrls.size() > MAX_MEDIA_COUNT) {
            throw new IllegalArgumentException("mediaUrls must have at most " + MAX_MEDIA_COUNT + " items");
        }
        for (String url : mediaUrls) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("mediaUrls must not contain blank entries");
            }
        }
        return List.copyOf(mediaUrls);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public PromotionChannel getChannel() {
        return channel;
    }

    public String getCaption() {
        return caption;
    }

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getSourceCommitSha() {
        return sourceCommitSha;
    }

    /** 반려되면 null이다 — 그 릴리스는 다시 후보가 된다. */
    public String getClaimedSourceCommitSha() {
        return claimedSourceCommitSha;
    }

    public PromotionPostStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getExternalPostId() {
        return externalPostId;
    }
}
