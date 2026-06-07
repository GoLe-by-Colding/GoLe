package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 요구사항 5.2: 사진 없이 리스팅 생성 시도.
 */
public class MissingPhotoException extends DomainException {

    public MissingPhotoException() {
        super("LISTING_PHOTO_REQUIRED", "At least one photo is required");
    }
}
