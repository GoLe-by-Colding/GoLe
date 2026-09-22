package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.NotFoundException;

/** 요청 ID 로 찾을 수 없을 때(promotion-review D20). */
public class PromotionDraftRequestNotFoundException extends NotFoundException {

    public PromotionDraftRequestNotFoundException(String requestId) {
        super("PROMOTION_DRAFT_REQUEST_NOT_FOUND", "Promotion draft request not found: " + requestId);
    }
}
