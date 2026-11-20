package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 요구사항 5.2: 사진 없이 리스팅 생성 시도.
 */
public class MissingPhotoException extends DomainException {

    public MissingPhotoException() {
        super("LISTING_PHOTO_REQUIRED", "사진을 한 장 이상 올려 주세요");
    }
}
