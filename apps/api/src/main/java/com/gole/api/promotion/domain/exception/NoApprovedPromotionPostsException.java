package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;

/** 승인된 글이 없는데 발행 실행을 요청했을 때. 에이전트를 띄워도 고를 것이 없다. */
public class NoApprovedPromotionPostsException extends ConflictException {

    public NoApprovedPromotionPostsException() {
        super("PROMOTION_NO_APPROVED_POSTS", "No approved promotion posts to publish");
    }
}
