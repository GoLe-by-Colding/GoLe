package com.gole.api.community.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class PostNotFoundException extends NotFoundException {

    public PostNotFoundException(String postId) {
        super("POST_NOT_FOUND", "Post not found: " + postId);
    }
}
