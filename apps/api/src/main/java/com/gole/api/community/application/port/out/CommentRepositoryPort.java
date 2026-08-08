package com.gole.api.community.application.port.out;

import com.gole.api.community.domain.model.Comment;
import java.util.List;

/**
 * 댓글 영속성 outbound port.
 */
public interface CommentRepositoryPort {

    /** 댓글을 저장하고 영속된 결과를 반환한다. */
    Comment save(Comment comment);

    /** 특정 게시글의 댓글 목록을 조회한다. (요구사항 12.3) */
    List<Comment> findByPostId(String postId, int limit);
}
