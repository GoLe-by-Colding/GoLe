package com.gole.api.community.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 발행 게시글에는 공백이 아닌 본문이 필요하다. 임시저장은 사진만 있어도 허용한다. */
public class PostContentRequiredException extends BadRequestException {

    public PostContentRequiredException() {
        super("POST_CONTENT_REQUIRED", "발행 게시글은 본문이 필요합니다");
    }
}
