package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 요구사항 5.3: 가격이 0 미만.
 */
public class InvalidPriceException extends DomainException {

    public InvalidPriceException() {
        super("INVALID_PRICE", "Price must be 0 or greater");
    }
}
