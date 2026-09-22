package com.gole.api.promotion.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.promotion.domain.exception.InvalidPromotionPostStateException;
import com.gole.api.promotion.domain.exception.SelfReviewNotAllowedException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionPostTest {

    private static final Instant NOW = Instant.EPOCH;
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private PromotionPost draft() {
        return PromotionPost.draft("promo-1", PromotionChannel.THREADS, "새 기능 나왔습니다", List.of(), "author-1", null, NOW);
    }

    private PromotionPost draftFrom(String sourceCommitSha) {
        return PromotionPost.draft(
                "promo-1", PromotionChannel.THREADS, "새 기능 나왔습니다", List.of(), "author-1", sourceCommitSha, NOW);
    }

    @Test
    void draftStartsInDraftStatus() {
        PromotionPost post = draft();
        assertThat(post.getStatus()).isEqualTo(PromotionPostStatus.DRAFT);
        assertThat(post.getAuthorId()).isEqualTo("author-1");
    }

    @Test
    void rejectsCaptionOver500Chars() {
        String tooLong = "a".repeat(501);
        assertThatThrownBy(() -> PromotionPost.draft(
                        "promo-1", PromotionChannel.THREADS, tooLong, List.of(), "author-1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsUpToTenMediaUrls() {
        List<String> tenUrls = List.of("u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8", "u9", "u10");
        PromotionPost post =
                PromotionPost.draft("promo-1", PromotionChannel.THREADS, "캡션", tenUrls, "author-1", null, NOW);

        assertThat(post.getMediaUrls()).hasSize(10);
    }

    @Test
    void rejectsMoreThanTenMediaUrls() {
        List<String> elevenUrls = List.of("u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8", "u9", "u10", "u11");

        assertThatThrownBy(() -> PromotionPost.draft(
                        "promo-1", PromotionChannel.THREADS, "캡션", elevenUrls, "author-1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankMediaUrlEntries() {
        assertThatThrownBy(() -> PromotionPost.draft(
                        "promo-1", PromotionChannel.THREADS, "캡션", List.of(" "), "author-1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sourceCommitSha는 소문자 40자 hex를 그대로 보관한다")
    void acceptsLowercaseHexSourceCommitSha() {
        PromotionPost post =
                PromotionPost.draft("promo-1", PromotionChannel.THREADS, "캡션", List.of(), "author-1", SHA, NOW);

        assertThat(post.getSourceCommitSha()).isEqualTo(SHA);
    }

    @Test
    @DisplayName("sourceCommitSha는 사람이 직접 쓴 초안을 위해 null을 허용한다")
    void allowsNullSourceCommitSha() {
        assertThat(draft().getSourceCommitSha()).isNull();
    }

    @Test
    @DisplayName("sourceCommitSha에 대문자가 섞이면 거부한다")
    void rejectsUppercaseSourceCommitSha() {
        assertThatThrownBy(() -> PromotionPost.draft(
                        "promo-1", PromotionChannel.THREADS, "캡션", List.of(), "author-1", SHA.toUpperCase(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sourceCommitSha가 40자가 아니면 거부한다")
    void rejectsSourceCommitShaOfWrongLength() {
        assertThatThrownBy(() -> PromotionPost.draft(
                        "promo-1", PromotionChannel.THREADS, "캡션", List.of(), "author-1", "abc", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void happyPathTransitionsThroughAllStates() {
        PromotionPost post = draft();

        post.submitForReview(NOW.plusSeconds(1));
        assertThat(post.getStatus()).isEqualTo(PromotionPostStatus.PENDING_REVIEW);
        assertThat(post.getSubmittedAt()).isEqualTo(NOW.plusSeconds(1));

        post.approve("reviewer-1", NOW.plusSeconds(2));
        assertThat(post.getStatus()).isEqualTo(PromotionPostStatus.APPROVED);
        assertThat(post.getReviewerId()).isEqualTo("reviewer-1");
        assertThat(post.getReviewedAt()).isEqualTo(NOW.plusSeconds(2));

        post.markPublished("threads-post-1", NOW.plusSeconds(3));
        assertThat(post.getStatus()).isEqualTo(PromotionPostStatus.PUBLISHED);
        assertThat(post.getExternalPostId()).isEqualTo("threads-post-1");
        assertThat(post.getPublishedAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void rejectReturnsToDraftWithReason() {
        PromotionPost post = draft();
        post.submitForReview(NOW);

        post.reject("reviewer-1", "오탈자 있음", NOW.plusSeconds(1));

        assertThat(post.getStatus()).isEqualTo(PromotionPostStatus.DRAFT);
        assertThat(post.getRejectionReason()).isEqualTo("오탈자 있음");
        assertThat(post.getReviewerId()).isEqualTo("reviewer-1");
    }

    @Test
    @DisplayName("새 초안은 자기 출처 릴리스를 곧바로 점유한다")
    void draftClaimsItsOwnSourceCommit() {
        PromotionPost post = draftFrom(SHA);

        assertThat(post.getSourceCommitSha()).isEqualTo(SHA);
        assertThat(post.getClaimedSourceCommitSha()).isEqualTo(SHA);
    }

    @Test
    @DisplayName("반려하면 릴리스 점유를 놓아주고 출처는 남는다")
    void rejectReleasesClaimButKeepsSourceCommitSha() {
        PromotionPost post = draftFrom(SHA);
        post.submitForReview(NOW);

        post.reject("reviewer-1", "오탈자 있음", NOW.plusSeconds(1));

        // 점유를 놓아줘야 그 릴리스가 다시 후보가 된다(D2) — 안 그러면 영영 홍보되지 않는다.
        assertThat(post.getClaimedSourceCommitSha()).isNull();
        // 출처는 불변이다 — 목록 화면의 "원본 릴리스" 표시와 재시도 집계가 이것을 본다.
        assertThat(post.getSourceCommitSha()).isEqualTo(SHA);
    }

    @Test
    void authorCannotApproveOwnPost() {
        PromotionPost post = draft();
        post.submitForReview(NOW);

        assertThatThrownBy(() -> post.approve("author-1", NOW.plusSeconds(1)))
                .isInstanceOf(SelfReviewNotAllowedException.class);
    }

    @Test
    void authorCannotRejectOwnPost() {
        PromotionPost post = draft();
        post.submitForReview(NOW);

        assertThatThrownBy(() -> post.reject("author-1", "사유", NOW.plusSeconds(1)))
                .isInstanceOf(SelfReviewNotAllowedException.class);
    }

    @Test
    void submitOnlyAllowedFromDraft() {
        PromotionPost post = draft();
        post.submitForReview(NOW);

        assertThatThrownBy(() -> post.submitForReview(NOW.plusSeconds(1)))
                .isInstanceOf(InvalidPromotionPostStateException.class);
    }

    @Test
    void approveOnlyAllowedFromPendingReview() {
        PromotionPost post = draft();

        assertThatThrownBy(() -> post.approve("reviewer-1", NOW))
                .isInstanceOf(InvalidPromotionPostStateException.class);
    }

    @Test
    void publishOnlyAllowedFromApproved() {
        PromotionPost post = draft();
        post.submitForReview(NOW);

        assertThatThrownBy(() -> post.markPublished("threads-post-1", NOW.plusSeconds(1)))
                .isInstanceOf(InvalidPromotionPostStateException.class);
    }
}
