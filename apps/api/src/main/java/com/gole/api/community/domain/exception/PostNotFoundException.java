package com.gole.api.community.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class PostNotFoundException extends NotFoundException {

    public PostNotFoundException(String postId) {
        super("POST_NOT_FOUND", "글을 찾을 수 없습니다");
    }
}
