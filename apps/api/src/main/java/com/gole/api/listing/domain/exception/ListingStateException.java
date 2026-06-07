package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 허용되지 않는 리스팅 상태 전이. (요구사항 5.8: 진행 중 주문이 있는 리스팅 삭제 거부 등)
 */
public class ListingStateException extends DomainException {

    public ListingStateException(String code, String message) {
        super(code, message);
    }
}
