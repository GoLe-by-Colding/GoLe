package com.gole.api.promotion.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionPostContextTest {

    private static final PromotionCapture CAPTURE =
            new PromotionCapture("필터 열린 목록", "/market", "필터 클릭", CaptureDataSource.DEMO, Instant.EPOCH);

    private static PromotionPost draftWith(List<String> mediaUrls, PromotionPostContext context) {
        return PromotionPost.draft(
                "promo-1", PromotionChannel.THREADS, "캡션", mediaUrls, "author-1", null, Instant.EPOCH, context);
    }

    @Test
    @DisplayName("맥락 없이 만든 글은 기능 홍보·설명표 없음으로 읽힌다")
    void draft_defaultsToFeatureWithoutCaptures() {
        PromotionPost post = PromotionPost.draft(
                "promo-1", PromotionChannel.THREADS, "캡션", List.of(), "author-1", null, Instant.EPOCH);

        assertThat(post.getCategory()).isEqualTo(PromotionCategory.FEATURE);
        assertThat(post.getCaptures()).isEmpty();
        assertThat(post.getProvenance()).isNull();
    }

    @Test
    @DisplayName("설명표는 스크린샷과 개수가 같아야 한다 — 어긋나면 검토 화면이 엉뚱한 사진에 붙인다")
    void draft_rejectsCapturesNotMatchingMedia() {
        var context = new PromotionPostContext(PromotionCategory.FEATURE, List.of(CAPTURE), null);

        assertThatThrownBy(() -> draftWith(List.of("/a.png", "/b.png"), context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(draftWith(List.of("/a.png"), context).getCaptures()).containsExactly(CAPTURE);
    }

    @Test
    @DisplayName("실행 로그 주소는 GitHub https 만 받는다 — 관리자 화면의 링크로 쓰이기 때문")
    void provenance_rejectsNonGithubRunUrl() {
        assertThatThrownBy(() -> new PromotionProvenance("제목", "이유", "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PromotionProvenance("제목", "이유", "https://github.com/o/r/actions/runs/1").runUrl())
                .startsWith("https://github.com/");
    }

    @Test
    @DisplayName("화면 경로는 / 로 시작해야 한다")
    void capture_rejectsNonPathRoute() {
        assertThatThrownBy(() -> new PromotionCapture("x", "https://evil", "", CaptureDataSource.DEMO, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다듬기 원본은 사이트 경로여야 하고 지시문과 함께 온다")
    void capture_originalMustBeSitePathAndPairedWithEdit() {
        var capture = new PromotionCapture("목록", "/search", "", CaptureDataSource.DEMO, Instant.EPOCH);

        assertThat(capture.withOriginal("/api/v1/media/images/raw.png", "목업").originalUrl())
                .isEqualTo("/api/v1/media/images/raw.png");
        assertThatThrownBy(() -> capture.withOriginal("https://evil.example/a.png", "목업"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> capture.withOriginal("//evil.example/a.png", "목업"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> capture.withOriginal("/api/v1/media/images/raw.png", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
