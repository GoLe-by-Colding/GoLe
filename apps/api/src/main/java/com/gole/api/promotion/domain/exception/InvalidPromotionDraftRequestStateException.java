package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.promotion.domain.model.PromotionDraftRequestStatus;

/** 요청의 현재 상태에서 허용되지 않는 전이를 시도했을 때(promotion-review D20). */
public class InvalidPromotionDraftRequestStateException extends ConflictException {

    public InvalidPromotionDraftRequestStateException(
            String requestId, PromotionDraftRequestStatus expected, PromotionDraftRequestStatus actual) {
        super(
                "PROMOTION_DRAFT_REQUEST_INVALID_STATE",
                "Promotion draft request " + requestId + " expected " + expected + " but was " + actual);
    }
}
