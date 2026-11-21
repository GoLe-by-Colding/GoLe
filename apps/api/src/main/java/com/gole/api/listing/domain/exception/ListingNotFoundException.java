package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class ListingNotFoundException extends NotFoundException {

    public ListingNotFoundException(String listingId) {
        super("LISTING_NOT_FOUND", "매물을 찾을 수 없습니다");
    }
}
