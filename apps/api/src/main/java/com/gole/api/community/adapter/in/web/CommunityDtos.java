package com.gole.api.community.adapter.in.web;

import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class CommunityDtos {

    private CommunityDtos() {}

    public record PublishPostRequest(
            @NotBlank String authorId, @NotBlank String content, List<@NotBlank String> imageUrls, String topic) {

        /** 이미지 미지정 시 빈 목록(텍스트 전용 글 허용). */
        public List<String> imageUrls() {
            return imageUrls == null ? List.of() : imageUrls;
        }
    }

    public record CommentRequest(@NotBlank String authorId, @NotBlank String content) {}

    public record LikeRequest(@NotBlank String userId) {}

    public record PostResponse(
            String id,
            String authorId,
            String content,
            List<String> imageUrls,
            String type,
            int likeCount,
            Instant createdAt) {

        public static PostResponse from(Post post) {
            return new PostResponse(
                    post.getId(),
                    post.getAuthorId(),
                    post.getContent(),
                    post.getImageUrls(),
                    post.getType().name().toLowerCase(),
                    post.likeCount(),
                    post.getCreatedAt());
        }
    }

    public record CommentResponse(String id, String authorId, String content, Instant createdAt) {

        public static CommentResponse from(Comment c) {
            return new CommentResponse(c.id(), c.authorId(), c.content(), c.createdAt());
        }
    }
}
