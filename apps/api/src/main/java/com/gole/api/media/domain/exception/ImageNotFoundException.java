package com.gole.api.media.domain.exception;

import com.gole.api.common.exception.NotFoundException;

/** 키에 해당하는 이미지 객체가 없을 때. (요구사항 M2.2) */
public class ImageNotFoundException extends NotFoundException {

    public ImageNotFoundException(String key) {
        super("IMAGE_NOT_FOUND", "Image not found: " + key);
    }
}
