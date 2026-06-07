package com.gole.api.order.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 요구사항 13.1: 동시 구매에서 이미 선점되어 구매 불가.
 */
public class ItemUnavailableException extends ConflictException {

    public ItemUnavailableException(String listingId) {
        super("ITEM_UNAVAILABLE", "Listing is no longer available: " + listingId);
    }
}
