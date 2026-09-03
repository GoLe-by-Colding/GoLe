package com.gole.api.community.application.port.out;

import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 게시글 영속성 outbound port. 애플리케이션은 저장 기술(MongoDB 등)에 의존하지 않는다.
 */
public interface PostRepositoryPort {

    /** 게시글을 저장(신규/갱신)하고 영속된 결과를 반환한다. */
    Post save(Post post);

    /** id로 단건 조회. 없으면 비어있음. */
    Optional<Post> findById(String postId);

    /** 게시된(PUBLISHED) 글을 최신→오래된 순으로 조회한다. (요구사항 12.6) */
    List<Post> findPublishedRecentFirst(int limit);

    /** createdAt/id 내림차순 키셋 커서로 게시글을 조회한다. */
    List<Post> findPublishedPage(
            int limit, Optional<FeedCursor> before, Optional<PostType> topic, Optional<String> query);

    /** 지정 작성자의 게시된 글만 최신순으로 조회한다(팔로잉 피드). */
    List<Post> findPublishedByAuthorIdsRecentFirst(List<String> authorIds, int limit);

    record FeedCursor(Instant createdAt, String postId) {}
}
