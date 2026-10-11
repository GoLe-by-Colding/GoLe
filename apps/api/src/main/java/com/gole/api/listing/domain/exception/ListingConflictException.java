package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 지금 상태에서는 판매자의 수정·끌올을 받을 수 없다(409). (listing-edit-and-bump E3, B3)
 *
 * <p>삭제 거부({@link ListingStateException}, 422)와 코드는 겹치지만 상태가 다르다. 수정·끌올은
 * "잠시 뒤 다시 시도하면 될 수도 있는" 경합 결과(주문 예약)까지 포함하므로 충돌로 돌려준다.
 */
public class ListingConflictException extends ConflictException {

    private ListingConflictException(String code, String message) {
        super(code, message);
    }

    /** 진행 중 주문(RESERVED)이 있다. 수정·끌올 공통. */
    public static ListingConflictException orderInProgress() {
        return new ListingConflictException("LISTING_ORDER_IN_PROGRESS", "진행 중인 주문이 있는 매물은 수정하거나 끌올할 수 없습니다");
    }

    /** 판매 완료·삭제된 매물은 수정할 수 없다. */
    public static ListingConflictException notEditable() {
        return new ListingConflictException("LISTING_NOT_EDITABLE", "판매 중인 매물만 수정할 수 있습니다");
    }

    /** 판매 중이 아닌 매물은 끌올할 수 없다. */
    public static ListingConflictException notBumpable() {
        return new ListingConflictException("LISTING_NOT_BUMPABLE", "판매 중인 매물만 끌올할 수 있습니다");
    }
}
