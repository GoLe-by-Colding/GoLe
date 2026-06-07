package com.gole.api.community.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 요구사항 12.5: 이미 좋아요한 게시글에 중복 좋아요.
 */
public class DuplicateLikeException extends ConflictException {

    public DuplicateLikeException() {
        super("DUPLICATE_LIKE", "Post already liked by this user");
    }
}
