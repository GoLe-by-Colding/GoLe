package com.gole.api.community.application.port.out;

import com.gole.api.community.domain.model.Comment;
import java.util.List;
import java.util.Optional;

/**
 * 댓글 영속성 outbound port.
 */
public interface CommentRepositoryPort {

    /** 댓글을 저장하고 영속된 결과를 반환한다. */
    Comment save(Comment comment);

    /** 운영 조치·신고 검증용 단건 조회. 숨김 댓글도 원문 보존을 위해 반환한다. */
    Optional<Comment> findById(String commentId);

    /** 특정 게시글의 공개 댓글만 조회한다. 블라인드된 댓글은 제외한다. */
    List<Comment> findByPostId(String postId, int limit);
}
