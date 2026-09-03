package com.gole.api.community.application.port.in;

import com.gole.api.community.domain.model.Comment;

/** 신고된 댓글을 검토하고 공개 목록에서 제외하는 운영자 유스케이스. */
public interface ModerateCommentUseCase {

    Comment getForModeration(String commentId);

    void hide(String commentId, String reason);
}
