package com.gole.api.community.adapter.in.web;

import com.gole.api.community.application.port.in.GetFeedUseCase.FeedPage;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import com.gole.api.report.domain.model.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CommunityDtos {

    private CommunityDtos() {}

    public record PublishPostRequest(
            String authorId,
            @NotBlank @Size(max = 5000) String content,
            @Size(max = 10) List<@NotBlank @Size(max = 2048) String> imageUrls,
            @Size(max = 50) String topic) {

        /** 이미지 미지정 시 빈 목록(텍스트 전용 글 허용). */
        public List<String> imageUrls() {
            return imageUrls == null ? List.of() : imageUrls;
        }
    }

    public record CommentRequest(String authorId, @NotBlank @Size(max = 1000) String content) {}

    public record ReportCommentRequest(
            @jakarta.validation.constraints.NotNull ReportReason reason, @Size(max = 1000) String detail) {}

    public record EditPostRequest(
            String requesterId,
            @NotBlank @Size(max = 5000) String content,
            @Size(max = 10) List<@NotBlank @Size(max = 2048) String> imageUrls) {

        public List<String> imageUrls() {
            return imageUrls == null ? List.of() : imageUrls;
        }
    }

    public record PostResponse(
            String id,
            String authorId,
            String content,
            List<String> imageUrls,
            String type,
            int likeCount,
            boolean likedByViewer,
            Instant createdAt) {

        public static PostResponse from(Post post) {
            return from(post, null);
        }

        public static PostResponse from(Post post, String viewerId) {
            return new PostResponse(
                    post.getId(),
                    post.getAuthorId(),
                    post.getContent(),
                    post.getImageUrls(),
                    post.getType().name().toLowerCase(),
                    post.likeCount(),
                    post.isLikedBy(viewerId),
                    post.getCreatedAt());
        }
    }

    public record FeedCursorResponse(Instant beforeCreatedAt, String beforeId) {}

    public record FeedPageResponse(List<PostResponse> items, FeedCursorResponse nextCursor) {

        public static FeedPageResponse from(FeedPage page, String viewerId) {
            List<PostResponse> items = page.items().stream()
                    .map(post -> PostResponse.from(post, viewerId))
                    .toList();
            if (!page.hasMore() || page.items().isEmpty()) {
                return new FeedPageResponse(items, null);
            }
            Post last = page.items().get(page.items().size() - 1);
            return new FeedPageResponse(items, new FeedCursorResponse(last.getCreatedAt(), last.getId()));
        }
    }

    public record CommentResponse(String id, String authorId, String content, Instant createdAt) {

        public static CommentResponse from(Comment c) {
            return new CommentResponse(c.id(), c.authorId(), c.content(), c.createdAt());
        }
    }
}
