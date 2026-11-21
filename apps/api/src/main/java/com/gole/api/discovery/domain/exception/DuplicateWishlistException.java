package com.gole.api.discovery.domain.exception;

import com.gole.api.common.exception.ConflictException;

/** 요구사항 17.3: 이미 위시리스트에 있는 대상. */
public class DuplicateWishlistException extends ConflictException {

    public DuplicateWishlistException() {
        super("DUPLICATE_WISHLIST", "이미 찜한 항목입니다");
    }
}
