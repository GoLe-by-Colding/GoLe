package com.gole.api.listing.domain.model;

import com.gole.api.listing.domain.exception.InvalidPriceException;

/**
 * 금액 값 객체(원 단위 정수). 음수를 허용하지 않는다. (요구사항 5.3)
 */
public record Money(long amount) {

    public Money {
        if (amount < 0) {
            throw new InvalidPriceException();
        }
    }

    public static Money won(long amount) {
        return new Money(amount);
    }
}
