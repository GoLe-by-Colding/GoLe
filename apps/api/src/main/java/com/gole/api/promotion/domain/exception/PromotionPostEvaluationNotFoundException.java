package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class PromotionPostEvaluationNotFoundException extends NotFoundException {

    public PromotionPostEvaluationNotFoundException(String promotionPostId) {
        super("PROMOTION_POST_EVALUATION_NOT_FOUND", "Promotion post evaluation not found: " + promotionPostId);
    }
}
