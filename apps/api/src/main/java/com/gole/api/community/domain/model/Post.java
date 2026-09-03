package com.gole.api.community.domain.model;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.community.domain.exception.DuplicateLikeException;
import com.gole.api.community.domain.exception.PostContentRequiredException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 커뮤니티 게시글 애그리거트. 자랑/MOC, 좋아요(중복 방지), 삭제를 캡슐화. (요구사항 12)
 */
public final class Post {

    private final String id;
    private final String authorId;
    private String content;
    private List<String> imageUrls;
    private final PostType type;
    private final Instant createdAt;
    private final Set<String> likedBy;
    private PostStatus status;

    public Post(
            String id,
            String authorId,
            String content,
            List<String> imageUrls,
            PostType type,
            PostStatus status,
            Set<String> likedBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorId = requireText(authorId, "authorId");
        this.content = Objects.requireNonNull(content, "content");
        // 이미지는 선택(질문·토론 등 텍스트 전용 글 허용). 자랑/MOC는 사진 권장.
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.likedBy = new LinkedHashSet<>(likedBy);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** 신규 게시글: 게시 상태로 생성. (요구사항 12.1, 12.2) */
    public static Post publish(
            String id, String authorId, String content, List<String> imageUrls, PostType type, Instant now) {
        return new Post(
                id,
                authorId,
                content,
                imageUrls,
                type == null ? PostType.GENERAL : type,
                PostStatus.PUBLISHED,
                Set.of(),
                now);
    }

    /** 좋아요. 중복 시 예외(요구사항 12.4, 12.5). */
    public void like(String userId) {
        if (likedBy.contains(userId)) {
            throw new DuplicateLikeException();
        }
        likedBy.add(userId);
    }

    /** 좋아요 취소는 재시도에 안전하도록 이미 취소된 상태에서도 성공한다. */
    public void unlike(String userId) {
        likedBy.remove(userId);
    }

    public boolean isLikedBy(String userId) {
        return userId != null && !userId.isBlank() && likedBy.contains(userId);
    }

    /** 본문/이미지 수정(작성자). 권한 검증은 애플리케이션 서비스에서 수행한다. */
    public void edit(String newContent, List<String> newImageUrls) {
        edit(newContent, newImageUrls, status);
    }

    /**
     * 부분 수정 결과를 반영한다. 임시저장은 빈 본문을 허용하지만 발행 결과에는 본문이 필요하다.
     */
    public void edit(String newContent, List<String> newImageUrls, PostStatus newStatus) {
        if (newContent == null) {
            throw new BadRequestException("INVALID_POST_BODY", "본문은 null일 수 없습니다");
        }
        if (newImageUrls == null) {
            throw new BadRequestException("INVALID_POST_PHOTOS", "사진 목록은 null일 수 없습니다");
        }
        if (newStatus == null || newStatus == PostStatus.DELETED) {
            throw new BadRequestException("INVALID_POST_STATUS", "게시글 상태는 draft 또는 published여야 합니다");
        }
        if (newStatus == PostStatus.PUBLISHED && newContent.isBlank()) {
            throw new PostContentRequiredException();
        }
        this.content = newContent;
        this.imageUrls = List.copyOf(newImageUrls);
        this.status = newStatus;
    }

    /** 삭제(요구사항 12.7). */
    public void delete() {
        this.status = PostStatus.DELETED;
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    public int likeCount() {
        return likedBy.size();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public PostType getType() {
        return type;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Set<String> getLikedBy() {
        return Collections.unmodifiableSet(likedBy);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
