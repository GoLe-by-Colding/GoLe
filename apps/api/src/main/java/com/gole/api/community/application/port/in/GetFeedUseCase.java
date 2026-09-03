package com.gole.api.community.application.port.in;

import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: 피드/게시글 조회. (요구사항 12.6)
 */
public interface GetFeedUseCase {

    /** 게시된 글을 최신→오래된 순으로. */
    List<Post> feed(int limit);

    /** 새 글이 추가돼도 중복·누락이 적은 키셋 커서 방식의 공개 피드 한 페이지. */
    FeedPage feedPage(int limit, Optional<FeedCursor> before, Optional<PostType> topic, Optional<String> query);

    default FeedPage feedPage(int limit, Optional<FeedCursor> before) {
        return feedPage(limit, before, Optional.empty(), Optional.empty());
    }

    Post getPost(String postId);

    List<Comment> comments(String postId, int limit);

    record FeedCursor(Instant createdAt, String postId) {}

    record FeedPage(List<Post> items, boolean hasMore) {}
}
