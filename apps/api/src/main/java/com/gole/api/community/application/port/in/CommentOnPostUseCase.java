package com.gole.api.community.application.port.in;

import com.gole.api.community.domain.model.Comment;

/**
 * Inbound port: 댓글 작성. (요구사항 12.3)
 */
public interface CommentOnPostUseCase {

    Comment comment(CommentCommand command);

    record CommentCommand(String postId, String authorId, String content) {}
}
