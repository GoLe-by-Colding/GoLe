package com.gole.api.community.application.port.in;

/** 작성자가 자신의 댓글을 공개 화면에서 삭제한다. 원문은 운영 증거로 보존한다. */
public interface DeleteCommentUseCase {

    void deleteComment(DeleteCommentCommand command);

    record DeleteCommentCommand(String postId, String commentId, String requesterId) {}
}
