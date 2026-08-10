package com.gole.api.order.domain.exception;

import com.gole.api.common.exception.ForbiddenException;

/**
 * 판매자가 자기 매물을 구매하려는 시도.
 *
 * <p>주문 완료는 체결가로 기록되어 시세의 원천이 되므로, 자기거래를 허용하면
 * 자전거래로 특정 세트의 시세를 임의로 만들 수 있다. 후기까지 자기 자신에게
 * 남길 수 있어 평점도 함께 오염된다.
 */
public class SelfPurchaseException extends ForbiddenException {

    public SelfPurchaseException(String listingId) {
        super("SELF_PURCHASE_NOT_ALLOWED", "Sellers cannot purchase their own listing: " + listingId);
    }
}
