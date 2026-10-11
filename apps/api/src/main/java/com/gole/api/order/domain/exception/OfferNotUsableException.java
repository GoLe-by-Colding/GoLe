package com.gole.api.order.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 주문에 실은 제안이 이 매물·이 구매자의 유효한 수락 제안이 아니다. (price-offer O16)
 *
 * <p>만료·철회·거절됐거나 다른 매물·다른 구매자의 제안이다. 정가로 다시 주문하거나 새로 제안하면 된다.
 */
public class OfferNotUsableException extends ConflictException {

    public OfferNotUsableException(String offerId) {
        super("OFFER_NOT_USABLE", "이 주문에 쓸 수 있는 수락된 가격 제안이 아닙니다: " + offerId);
    }
}
