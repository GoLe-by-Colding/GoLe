package com.gole.api.community.application.port.in;

import com.gole.api.community.domain.model.Comment;
import com.gole.api.community.domain.model.Post;
import java.util.List;

/**
 * Inbound port: 피드/게시글 조회. (요구사항 12.6)
 */
public interface GetFeedUseCase {

    /** 게시된 글을 최신→오래된 순으로. */
    List<Post> feed(int limit);

    Post getPost(String postId);

    List<Comment> comments(String postId, int limit);
}
