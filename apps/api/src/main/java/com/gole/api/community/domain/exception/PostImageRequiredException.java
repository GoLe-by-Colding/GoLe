package com.gole.api.community.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 요구사항 12.1: 게시글에는 최소 1장의 이미지가 필요.
 */
public class PostImageRequiredException extends DomainException {

    public PostImageRequiredException() {
        super("POST_IMAGE_REQUIRED", "At least one image is required");
    }
}
